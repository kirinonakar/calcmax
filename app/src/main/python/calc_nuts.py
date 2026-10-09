"""Seeded slice NUTS with recursive doubling and warmup-only dual averaging.

Hoffman & Gelman (2014), Algorithms 4 and 6:
https://jmlr.org/papers/v15/hoffman14a.html
The caller supplies a whitened negative log density and analytic gradient.
Diagnostics use rank-normalized/folded split R-hat, bulk ESS and tail ESS.
"""
from calc_limits import within_limit
import math
import random
from statistics import NormalDist
from calc_shared import MathError, require


def quantile(values, probability):
    values = sorted(values)
    position = probability*(len(values)-1)
    lower = int(position); fraction = position-lower
    return values[lower]*(1-fraction)+values[min(lower+1,len(values)-1)]*fraction


def _autocovariance(chain, average):
    # Radix-2 FFT keeps full-lag ESS practical without NumPy in WASM/Android.
    n=len(chain); length=1
    while length<2*n: length*=2
    values=[complex(v-average) for v in chain]+[0j]*(length-n)
    def fft(values, inverse=False):
        j=0
        for i in range(1,length):
            bit=length>>1
            while j&bit: j^=bit; bit>>=1
            j^=bit
            if i<j: values[i],values[j]=values[j],values[i]
        width=2
        while width<=length:
            angle=(2 if inverse else -2)*math.pi/width
            root=complex(math.cos(angle),math.sin(angle))
            for start in range(0,length,width):
                factor=1+0j
                for k in range(width//2):
                    u=values[start+k]; v=values[start+k+width//2]*factor
                    values[start+k]=u+v; values[start+k+width//2]=u-v; factor*=root
            width*=2
        if inverse:
            for i in range(length): values[i]/=length
    fft(values)
    values=[complex(abs(v)**2) for v in values]
    fft(values,True)
    return [values[i].real/n for i in range(n)]


def _split_statistics(split):
    size=len(split[0])
    count = len(split)
    means = [math.fsum(chain)/size for chain in split]
    average = math.fsum(means)/count
    variances = [math.fsum((v-m)**2 for v in chain)/(size-1) for chain,m in zip(split,means)]
    within = math.fsum(variances)/count
    between = size*math.fsum((m-average)**2 for m in means)/(count-1)
    variance = (size-1)/size*within+between/size
    if within <= 0 or variance <= 0: return None, 0.0
    rhat = math.sqrt(variance/within)
    autocovariances=[_autocovariance(chain,m) for chain,m in zip(split,means)]
    def rho(lag):
        cov = math.fsum(c[lag] for c in autocovariances)/count
        return 1-(within-cov)/variance
    correlations=[0.0]*size; even=1.0; correlations[0]=even
    odd=rho(1); correlations[1]=odd; lag=1
    while lag<size-3 and even+odd>0:
        even,odd=rho(lag+1),rho(lag+2)
        if even+odd>=0: correlations[lag+1:lag+3]=[even,odd]
        lag+=2
    last=lag-2
    if even>0: correlations[last+1]=even
    for lag in range(1,last-1,2):
        previous=correlations[lag-1]+correlations[lag]
        if correlations[lag+1]+correlations[lag+2]>previous:
            correlations[lag+1]=correlations[lag+2]=previous/2
    total=count*size
    tau=-1+2*math.fsum(correlations[:last+1])+math.fsum(correlations[last+1:last+2])
    ess=total/max(1/math.log10(total),tau)
    return rhat, ess


def _rank_normalize(chains):
    values=[v for chain in chains for v in chain]; total=len(values)
    ordered=sorted(range(total),key=values.__getitem__); scores=[0.0]*total
    normal=NormalDist(); first=0
    while first<total:
        last=first+1
        while last<total and values[ordered[last]]==values[ordered[first]]: last+=1
        rank=(first+1+last)/2  # Average ranks for ties; Blom's transform.
        score=normal.inv_cdf((rank-.375)/(total+.25))
        for i in ordered[first:last]: scores[i]=score
        first=last
    size=len(chains[0])
    return [scores[i:i+size] for i in range(0,total,size)]


def rank_diagnostics(chains):
    require(len(chains)>=2 and len({len(c) for c in chains})==1 and len(chains[0])>=4,
            'Diagnostics require at least two equal chains with four draws')
    require(all(math.isfinite(v) for c in chains for v in c),'Non-finite posterior draws')
    size=len(chains[0])//2
    split=[part for chain in chains for part in (chain[:size],chain[-size:])]
    values=[v for chain in split for v in chain]
    ranked=_rank_normalize(split)
    rhat,bulk=_split_statistics(ranked)
    median=quantile(values,.5)
    folded,_=_split_statistics(_rank_normalize([[abs(v-median) for v in chain] for chain in split]))
    low,high=quantile(values,.05),quantile(values,.95)
    tail=min(_split_statistics([[float(v<=cut) for v in chain] for chain in split])[1] for cut in (low,high))
    _,mean_ess=_split_statistics(split)
    return {'rHat':max(rhat,folded) if rhat is not None and folded is not None else None,
            'bulkEss':bulk,'tailEss':tail,'ess':mean_ess}


def diagnostics(chains):
    result=rank_diagnostics(chains)
    return result['rHat'],result['ess']


def _leapfrog(target, state, epsilon):
    position, momentum, _, gradient = state
    half = [r-epsilon*g/2 for r,g in zip(momentum,gradient)]
    position = [q+epsilon*r for q,r in zip(position,half)]
    value, gradient = target(position)
    momentum = [r-epsilon*g/2 for r,g in zip(half,gradient)]
    if not math.isfinite(value) or not all(math.isfinite(v) for v in position+momentum+gradient):
        raise FloatingPointError('Non-finite NUTS trajectory')
    return position, momentum, value, gradient


def _joint(state):
    return -state[2]-math.fsum(v*v for v in state[1])/2


def _no_u_turn(left, right):
    displacement = [b-a for a,b in zip(left[0],right[0])]
    return (math.fsum(d*r for d,r in zip(displacement,left[1])) >= 0 and
            math.fsum(d*r for d,r in zip(displacement,right[1])) >= 0)


def _build_tree(target, state, log_slice, direction, depth, epsilon, initial_joint, rng):
    if depth == 0:
        try:
            next_state = _leapfrog(target,state,direction*epsilon)
            joint = _joint(next_state)
            divergent = not math.isfinite(joint) or initial_joint-joint > 1000
            continuing = not divergent and log_slice < joint+1000
            count = int(not divergent and log_slice <= joint)
            alpha = math.exp(min(0.0,joint-initial_joint)) if math.isfinite(joint) else 0.0
        except MathError:
            raise  # Workload/cancellation failures must never become rejected draws.
        except (OverflowError,ValueError,ZeroDivisionError,FloatingPointError):
            next_state = state; count = 0; continuing = False; alpha = 0.0; divergent = True
        return next_state,next_state,next_state,count,continuing,alpha,1,divergent
    left,right,candidate,count,continuing,alpha,steps,divergent = _build_tree(
        target,state,log_slice,direction,depth-1,epsilon,initial_joint,rng)
    if continuing:
        other_left,other_right,other_candidate,other_count,other_continuing,other_alpha,other_steps,other_divergent = _build_tree(
            target,left if direction < 0 else right,log_slice,direction,depth-1,epsilon,initial_joint,rng)
        if direction < 0: left = other_left
        else: right = other_right
        total = count+other_count
        if total and rng.random() < other_count/total: candidate = other_candidate
        count = total
        continuing = other_continuing and _no_u_turn(left,right)
        alpha += other_alpha; steps += other_steps; divergent = divergent or other_divergent
    return left,right,candidate,count,continuing,alpha,steps,divergent


def _initial_step_size(target, state):
    epsilon = 1.0
    def acceptance(step):
        try:
            return math.exp(min(0.0,_joint(_leapfrog(target,state,step))-_joint(state)))
        except MathError:
            raise
        except (OverflowError,ValueError,ZeroDivisionError,FloatingPointError):
            return 0.0
    probability = acceptance(epsilon)
    direction = 1 if probability > .5 else -1
    for _ in range(20):
        if (probability > .5) != (direction > 0): break
        proposal = epsilon*(2 if direction > 0 else .5)
        if not 1e-5 <= proposal <= 2.0: break
        epsilon = proposal; probability = acceptance(epsilon)
    return epsilon


def sample(target, dimensions, samples=500, warmup=500, max_depth=8, seed=0, chains=2,
           max_evaluations=None):
    require(100 <= samples and within_limit(samples,5000) and 50 <= warmup and within_limit(warmup,5000) and 1 <= max_depth and within_limit(max_depth,10) and
            0 <= seed <= 2147483647 and 2 <= chains and within_limit(chains,4), 'NUTS options: samples 100–5000, warmup 50–5000, max tree depth 1–10, seed 0–2147483647, chains 2–4')
    require(isinstance(dimensions,int) and dimensions > 0, 'NUTS requires positive dimensions')
    evaluations = 0
    def evaluate(position):
        nonlocal evaluations
        evaluations += 1
        require(max_evaluations is None or within_limit(evaluations,max_evaluations),
                'NUTS workload exceeds 200 million row/parameter gradient evaluations; reduce data or sampler settings')
        return target(position)
    draws = []; summaries = []
    for chain_id in range(chains):
        rng = random.Random(seed+104729*chain_id)
        position = [rng.gauss(0,1) for _ in range(dimensions)]
        value, gradient = evaluate(position)
        require(math.isfinite(value) and all(math.isfinite(v) for v in gradient), 'NUTS target exceeds floating point range')
        initial_momentum = [rng.gauss(0,1) for _ in position]
        step_size = _initial_step_size(evaluate,(position,initial_momentum,value,gradient))
        mu = math.log(10*step_size); log_average = math.log(step_size); hbar = 0.0
        kept = []; divergences = 0; acceptance_sum = 0.0; depth_hits = 0; depth_sum = 0; leapfrog_sum = 0
        for iteration in range(1,warmup+samples+1):
            momentum = [rng.gauss(0,1) for _ in position]
            state = (position,momentum,value,gradient)
            initial_joint = _joint(state)
            log_slice = initial_joint-rng.expovariate(1)
            left = right = candidate = state
            count = 1; continuing = True; depth = 0; divergent = False; total_steps = 0; total_alpha = 0.0
            while continuing and depth < max_depth:
                direction = -1 if rng.random() < .5 else 1
                other_left,other_right,other_candidate,other_count,other_continuing,alpha,steps,other_divergent = _build_tree(
                    evaluate,left if direction < 0 else right,log_slice,direction,depth,step_size,initial_joint,rng)
                if direction < 0: left = other_left
                else: right = other_right
                # Algorithm 6 uses n_new/n_old at the top level; inside each
                # subtree selection is uniform with n_right/(n_left+n_right).
                if other_continuing and rng.random() < min(1.0,other_count/count):
                    candidate = other_candidate
                count += other_count
                continuing = other_continuing and _no_u_turn(left,right)
                depth += 1; total_steps += steps; total_alpha += alpha; divergent = divergent or other_divergent
            position,_,value,gradient = candidate
            probability = total_alpha/total_steps
            if iteration <= warmup:
                eta = 1/(iteration+10)
                hbar = (1-eta)*hbar+eta*(.8-probability)
                log_step = max(math.log(1e-5),min(math.log(2.0),mu-math.sqrt(iteration)/.05*hbar))
                weight = iteration**-.75
                log_average = weight*log_step+(1-weight)*log_average
                step_size = math.exp(log_average if iteration == warmup else log_step)
            else:
                kept.append(position[:]); divergences += int(divergent); acceptance_sum += probability
                depth_hits += int(continuing and depth == max_depth)
                depth_sum += depth; leapfrog_sum += total_steps
        draws.append(kept)
        summaries.append({'chain':chain_id+1,'meanAcceptanceProbability':acceptance_sum/samples,
                          'divergences':divergences,'stepSize':step_size,'maxTreeDepthHits':depth_hits,
                          'meanTreeDepth':depth_sum/samples,'meanLeapfrogSteps':leapfrog_sum/samples})
    return draws, {'samples':samples,'warmup':warmup,'maxTreeDepth':max_depth,'seed':seed,'chains':chains,
                   'totalSamples':samples*chains,'divergences':sum(c['divergences'] for c in summaries),
                   'meanAcceptanceProbability':sum(c['meanAcceptanceProbability'] for c in summaries)/chains,
                   'maxTreeDepthHits':sum(c['maxTreeDepthHits'] for c in summaries),
                   'meanTreeDepth':sum(c['meanTreeDepth'] for c in summaries)/chains,
                   'meanLeapfrogSteps':sum(c['meanLeapfrogSteps'] for c in summaries)/chains,
                   'gradientEvaluations':evaluations,'targetAcceptance':.8,'chainDiagnostics':summaries,
                   'diagnosticMethod':'rank-normalized/folded split R-hat; bulk/tail ESS; mean ESS for MCSE (full-lag initial monotone sequence)'}

