"""Independent research mathematics; no Android dependency or GPL implementation.

Rates are empirical hypotheses, never inferred physiological clearance here.
All kernels return increments per active 17beta-E2 mg, in pg/mL.
"""
import math
from dataclasses import dataclass

PMOL_PER_PG = 3.671

def positive(*values):
    if any(not math.isfinite(x) or x <= 0 for x in values):
        raise ValueError('Expected finite positive parameter')

def time(t):
    if not math.isfinite(t):
        raise ValueError('Expected finite time')

def active_e2_mg(dose, compound='E2'):
    if not math.isfinite(dose) or dose < 0:
        raise ValueError('Invalid dose')
    if compound == 'E2':
        return dose
    if compound == 'EV':
        return dose * 272.39 / 356.51
    raise ValueError('Unsupported compound')

def pg_ml(value, unit):
    if not math.isfinite(value) or value < 0:
        raise ValueError('Invalid concentration')
    if unit == 'pg/mL':
        return value
    if unit == 'pmol/L':
        return value / PMOL_PER_PG
    raise ValueError('Unsupported concentration unit')

def q(t, ka, ke):
    """Independent solution of dx/dt=ka*exp(-ka*t)-ke*x.

    Factoring the slower exponential avoids overflow when ka<ke. expm1
    preserves small differences; exact equality uses the continuous limit.
    """
    positive(ka, ke)
    time(t)
    if t <= 0:
        return 0.0
    delta = abs(ka - ke)
    if delta == 0:
        return ka * t * math.exp(-ka * t)
    z = delta * t
    # t*(1-exp(-z))/z also remains accurate when z is subnormal.
    factor = 1.0 if z == 0 else -math.expm1(-z) / z
    return ka * t * math.exp(-min(ka, ke) * t) * factor

def q_integral(t, ka, ke):
    """Analytic integrated ODE mass balance; near equality uses gamma limit.

    General near-equal oracle checked independently by numerical integration.
    """
    positive(ka, ke)
    time(t)
    if t <= 0:
        return 0.0
    return (-math.expm1(-ka*t) - q(t, ka, ke)) / ke

@dataclass(frozen=True)
class Gamma:
    exposure: float
    k: float
    def __post_init__(self):
        positive(self.exposure, self.k)
    def __call__(self, t):
        time(t)
        if t <= 0:
            return 0.0
        return self.exposure * self.k * (self.k * t) * math.exp(-self.k*t)
    def integral(self, t):
        time(t)
        if t <= 0:
            return 0.0
        # series avoids cancellation for very short integration windows.
        x = self.k*t
        if x < 1e-4:
            mass = math.fsum((-1)**n * (n-1) * x**n / math.factorial(n) for n in range(2, 10))
        else:
            mass = -math.expm1(-x) - x * math.exp(-x)
        return self.exposure * mass

@dataclass(frozen=True)
class Dual:
    gain: float
    kam: float
    kag: float
    ke: float
    slow_share: float
    def __post_init__(self):
        positive(self.gain, self.kam, self.kag, self.ke)
        if not math.isfinite(self.slow_share) or not 0 <= self.slow_share <= 1:
            raise ValueError('Invalid effective share')
    def __call__(self, t):
        return self.gain * ((1-self.slow_share)*q(t,self.kam,self.ke)+self.slow_share*q(t,self.kag,self.ke))
    def integral(self, t):
        return self.gain * ((1-self.slow_share)*q_integral(t,self.kam,self.ke)+self.slow_share*q_integral(t,self.kag,self.ke))

def from_micro(fm, Fm, Fg, volume, kam=4.0, kag=0.32, ke=0.41):
    if any(not math.isfinite(x) or not 0 <= x <= 1 for x in [fm,Fm,Fg]):
        raise ValueError('Invalid microscopic fraction')
    positive(volume)
    gm = 1e6 * fm*Fm/volume
    gg = 1e6 * (1-fm)*Fg/volume
    return Dual(gm+gg,kam,kag,ke,gg/(gm+gg))

def fitted(t, model):
    time(t)
    if t <= 0:
        return 0.0
    ka = model['ka_per_h']
    return math.fsum(x['A_per_mg'] * math.exp(-x['lambda_per_h']*t) *
                     -math.expm1(-(ka-x['lambda_per_h'])*t) for x in model['terms'])

def current(t, params, hold_minutes=10):
    positive(hold_minutes)
    sl = params['models']['E2_SL']
    # Production tier transform; extrapolation, not measured time/fraction relation.
    default = sl['tier_minutes'][sl['default_tier']]
    mucosal0 = 1-sl['swallowed_share']
    mucosal = min(1.0, mucosal0*hold_minutes/default)
    return mucosal/mucosal0*fitted(t,sl) + (1-mucosal)*fitted(t,params['models']['E2_ORAL'])

def feather(t, meta, weight=80):
    positive(weight)
    fast = meta['mucosal_fraction']*meta['fast_bioavailability']*q(t,meta['k_fast_per_h'],meta['k_elimination_per_h'])
    slow = (1-meta['mucosal_fraction'])*meta['oral_bioavailability']*q(t,meta['k_oral_per_h'],meta['k_elimination_per_h'])
    return 1e6/(meta['volume_l_per_kg']*weight)*(fast+slow)

def population(kernel, events, at, baseline=0):
    """Events (actual_hour, active_E2_mg); skipped events are absent, no mutation."""
    time(at)
    if not math.isfinite(baseline) or baseline < 0:
        raise ValueError('Invalid baseline')
    for hour, dose in events:
        time(hour)
        active_e2_mg(dose)
    return baseline + math.fsum(dose*kernel(at-hour) for hour,dose in events if hour <= at)

def repeat_events(interval, days=30, dose=2):
    positive(interval,days)
    active_e2_mg(dose)
    return [(i*interval,dose) for i in range(math.ceil(24*days/interval))]

def simpson(f, upper, n=4000):
    if n <= 0 or n % 2 or upper < 0:
        raise ValueError('Invalid integration grid')
    h = upper/n
    return h/3*(f(0)+f(upper)+4*math.fsum(f(i*h) for i in range(1,n,2))+2*math.fsum(f(i*h) for i in range(2,n,2)))

def ode_oracle(t, kam, kag, ke, gm, gg, steps=20000):
    """Classical RK4 for independent 3-state ODE, not using q/closed-form."""
    positive(kam,kag,ke)
    if t < 0:
        return 0.0
    dt=t/steps
    state=[1.0,1.0,0.0]
    def rhs(y):return [-kam*y[0],-kag*y[1],gm*kam*y[0]+gg*kag*y[1]-ke*y[2]]
    for _ in range(steps):
        a=rhs(state);b=rhs([x+dt*v/2 for x,v in zip(state,a)])
        c=rhs([x+dt*v/2 for x,v in zip(state,b)]);d=rhs([x+dt*v for x,v in zip(state,c)])
        state=[x+dt*(v+2*w+2*z+u)/6 for x,v,w,z,u in zip(state,a,b,c,d)]
    return state[2]

def amplitude_fit(unit_prediction, target, bounds, starts):
    """Convex one-parameter LS; closed form plus independent log-space search.

    Each preset start sets an initial bracket using the signed derivative of
    the one-dimensional objective. This does not identify a missing rate.
    """
    positive(unit_prediction,target,*bounds)
    lo,hi=bounds
    if lo >= hi:
        raise ValueError('Reversed bounds')
    analytic=target/unit_prediction
    if not lo <= analytic <= hi:
        return {'status':'amplitude_outside_prespecified_bounds','unbounded_amplitude':analytic}
    trials=[]
    for start in starts:
        positive(start)
        if not lo <= start <= hi:
            raise ValueError('Start outside prespecified bounds')
        left,right=math.log(lo),math.log(hi)
        # The objective decreases until unit_prediction*amplitude == target.
        # Unlike a cosmetic start label, this changes the actual search bracket.
        if unit_prediction*start < target:
            left=math.log(start)
        elif unit_prediction*start > target:
            right=math.log(start)
        initial_bracket=[math.exp(left),math.exp(right)]
        # independent ternary minimization of unimodal squared prediction error
        for _ in range(160):
            a=(2*left+right)/3;b=(left+2*right)/3
            fa=(unit_prediction*math.exp(a)-target)**2
            fb=(unit_prediction*math.exp(b)-target)**2
            if fa < fb:right=b
            else:left=a
        answer=math.exp((left+right)/2)
        trials.append({'start':start,'initial_bracket':initial_bracket,'solution':answer,'squared_error':(unit_prediction*answer-target)**2})
    return {'status':'conditional_fit_only','amplitude':analytic,'residual_squared':(analytic*unit_prediction-target)**2,'at_boundary':analytic==lo or analytic==hi,'starts':trials}
