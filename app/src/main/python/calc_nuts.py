"""Seeded slice NUTS with recursive doubling and warmup-only dual averaging.

Hoffman & Gelman (2014), Algorithms 4 and 6:
https://jmlr.org/papers/v15/hoffman14a.html
The caller supplies a whitened negative log density and analytic gradient.
Diagnostics are classical split R-hat and autocorrelation ESS (not rank ESS).
"""
from calc_limits import within_limit
import math
import random
from calc_shared import MathError, require


def quantile(values, probability):
    values = sorted(values)
    position = probability*(len(values)-1)
    lower = int(position); fraction = position-lower
    return values[lower]*(1-fraction)+values[min(lower+1,len(values)-1)]*fraction


def diagnostics(chains):
    size = len(chains[0])//2
    split = [part for chain in chains for part in (chain[:size],chain[-size:])]
    count = len(split)
    means = [math.fsum(chain)/size for chain in split]
    average = math.fsum(means)/count
    variances = [math.fsum((v-m)**2 for v in chain)/(size-1) for chain,m in zip(split,means)]
    within = math.fsum(variances)/count
    between = size*math.fsum((m-average)**2 for m in means)/(count-1)
    variance = (size-1)/size*within+between/size
    if within <= 0 or variance <= 0: return None, 0.0
    rhat = math.sqrt(variance/within)
    def rho(lag):
        # Biased autocovariance is stable at long lags and matches rho(0).
        cov = math.fsum(math.fsum((chain[i]-m)*(chain[i+lag]-m) for i in range(size-lag))/size
                        for chain,m in zip(split,means))/count
        return 1-(within-cov)/variance
    pair_sum = 0.0; previous = math.inf
    for lag in range(0,min(size-1,1000),2):
        pair = (1.0 if lag == 0 else rho(lag))+rho(lag+1)
        if pair <= 0: break
        previous = min(previous,pair)
        pair_sum += previous
    ess = min(count*size, count*size/max(1.0,2*pair_sum-1))
    return rhat, ess


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
            count = 1; continuing = True; depth = 0; divergent = False; total_steps = 0
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
                depth += 1; total_steps += steps; divergent = divergent or other_divergent
            position,_,value,gradient = candidate
            probability = alpha/steps  # Acceptance statistic of the final doubling.
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
                   'diagnosticMethod':'classical split R-hat; initial monotone autocorrelation ESS (up to 1000 lags)'}

