"""Seeded static HMC with Metropolis correction and warmup-only dual averaging.

The caller supplies a whitened target and analytic gradient. Trajectory length
and step size are jittered to avoid resonances; this is static HMC, not NUTS.
Diagnostics are classical split R-hat and autocorrelation ESS (not rank ESS).
"""
import math
import random
from calc_shared import require


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


def sample(target, dimensions, samples=500, warmup=500, leapfrog=10, seed=0, chains=2):
    require(100 <= samples <= 5000 and 50 <= warmup <= 5000 and 1 <= leapfrog <= 50 and
            0 <= seed <= 2147483647 and 2 <= chains <= 4, 'HMC options: samples 100–5000, warmup 50–5000, leapfrog 1–50, seed 0–2147483647, chains 2–4')
    draws = []; summaries = []
    for chain_id in range(chains):
        rng = random.Random(seed+104729*chain_id)
        position = [rng.gauss(0,1) for _ in range(dimensions)]
        value, gradient = target(position)
        require(math.isfinite(value) and all(math.isfinite(v) for v in gradient), 'HMC target exceeds floating point range')
        step_size = .15; mu = math.log(10*step_size); log_average = math.log(step_size); hbar = 0.0
        kept = []; accepted = 0; divergences = 0; acceptance_sum = 0.0
        for iteration in range(1,warmup+samples+1):
            momentum = [rng.gauss(0,1) for _ in position]
            proposed = position[:]
            velocity = momentum[:]
            epsilon = step_size*rng.uniform(.9,1.1)
            steps = max(1,round(leapfrog*rng.uniform(.8,1.2)))
            proposed_value = value; proposed_gradient = gradient
            divergent = False
            try:
                velocity = [v-epsilon*g/2 for v,g in zip(velocity,gradient)]
                for leap in range(steps):
                    proposed = [q+epsilon*v for q,v in zip(proposed,velocity)]
                    proposed_value, proposed_gradient = target(proposed)
                    if not math.isfinite(proposed_value) or not all(math.isfinite(g) for g in proposed_gradient):
                        divergent = True; break
                    fraction = .5 if leap == steps-1 else 1.0
                    velocity = [v-fraction*epsilon*g for v,g in zip(velocity,proposed_gradient)]
                delta = proposed_value-value+math.fsum(v*v-m*m for v,m in zip(velocity,momentum))/2
                divergent = divergent or not math.isfinite(delta) or abs(delta) > 1000
                probability = 0.0 if divergent else math.exp(min(0.0,-delta))
            except (OverflowError,ValueError,ZeroDivisionError):
                probability = 0.0; divergent = True
            moved = rng.random() < probability
            if moved:
                position = proposed; value = proposed_value; gradient = proposed_gradient
            if iteration <= warmup:
                eta = 1/(iteration+10)
                hbar = (1-eta)*hbar+eta*(.8-probability)
                log_step = max(math.log(1e-5),min(math.log(2.0),mu-math.sqrt(iteration)/.05*hbar))
                weight = iteration**-.75
                log_average = weight*log_step+(1-weight)*log_average
                step_size = math.exp(log_average if iteration == warmup else log_step)
            else:
                kept.append(position[:]); accepted += int(moved); divergences += int(divergent); acceptance_sum += probability
        draws.append(kept)
        summaries.append({'chain':chain_id+1,'acceptanceRate':accepted/samples,
                          'meanAcceptanceProbability':acceptance_sum/samples,'divergences':divergences,'stepSize':step_size})
    return draws, {'samples':samples,'warmup':warmup,'leapfrog':leapfrog,'seed':seed,'chains':chains,
                   'totalSamples':samples*chains,'divergences':sum(c['divergences'] for c in summaries),
                   'acceptanceRate':sum(c['acceptanceRate'] for c in summaries)/chains,'chainDiagnostics':summaries,
                   'diagnosticMethod':'classical split R-hat; initial monotone autocorrelation ESS (up to 1000 lags)'}
