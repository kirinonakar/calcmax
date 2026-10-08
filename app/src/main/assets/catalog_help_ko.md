# 카탈로그 함수 설명서

함수 카탈로그는 빈칸이 있는 템플릿을 현재 편집기에 넣습니다. 템플릿을 누른 뒤 `[]` 또는 `round(x,)`처럼 비어 있는 인수를 눌러 값을 입력하세요. Python 모드에서는 `calc.<function>(...)`를 넣고 `import symvacas_catalog as calc` 및 기호 `x, y, z, t, pi`를 파일 위쪽에 한 번 추가합니다.

위 검색창에서 함수 이름, 템플릿, 예제 또는 설명으로 찾을 수 있습니다. 모든 범주를 한꺼번에 검색합니다. 전체 목록을 다시 보려면 검색어를 지우세요.

예제를 누르면 해당 수식이 계산기 입력창에 바로 들어갑니다.

## 카탈로그 사용법
- 수치 삼각함수는 선택한 DEG/RAD/GRAD 각도 단위를 따릅니다. 수식에 `pi` 또는 `°`를 명시하면 해당 단위를 우선합니다.
- 기호 미적분은 항상 라디안으로 계산합니다.
- 행렬과 벡터 명령에는 `[[1,2],[3,4]]`처럼 값을 직접 입력할 수 있습니다.
- 저장한 사용자 함수는 카탈로그의 `Custom` 범주에 표시됩니다.
- Functions 화면에서 사용자 함수 목록을 JSON 파일로 내보내거나 가져올 수 있습니다. 가져올 때 각 정의를 검사하고 추가·교체·건너뛴 항목 수를 알려 줍니다.
- 범주별 사용법은 카탈로그 목록 아래의 안내 문구를 확인하세요.

## Scientific
`sin(x)` — x의 사인값(현재 각도 단위 사용).
Example: sin(pi/6)
`cos(x)` — x의 코사인값(현재 각도 단위 사용).
Example: cos(0)
`tan(x)` — x의 탄젠트값(현재 각도 단위 사용).
Example: tan(pi/4)
`asin(x)` — 역사인값을 현재 각도 단위로 반환합니다.
Example: asin(1)
`acos(x)` — 역코사인값을 현재 각도 단위로 반환합니다.
Example: acos(0)
`atan(x)` — 역탄젠트값을 현재 각도 단위로 반환합니다.
Example: atan(1)
`abs(x)` — 절댓값, 크기 또는 복소수의 절댓값 |x|.
Example: abs(-3)
`floor(x)` — x 이하인 최대 정수.
Example: floor(2.7)
`ceil(x)` — x 이상인 최소 정수.
Example: ceil(2.1)
`round(x,n)` — x를 소수점 아래 n자리로 은행가 반올림(정확히 중간이면 짝수 쪽)합니다. 기본값은 n=0입니다.
Example: round(2.5) → 2 (banker's rounding)
`roundh(x,n)` — x를 소수점 아래 n자리로 사사오입(정확히 중간이면 0에서 멀어지는 쪽)합니다. 기본값은 n=0입니다.
Example: roundh(2.5) → 3 (round half up)
`sign(x)` — x의 부호: −1, 0 또는 1.
Example: sign(-5)
`sqrt(x)` — 제곱근.
Example: sqrt(16)
`cbrt(x)` — 세제곱근.
Example: cbrt(27)
`nthroot(x,n)` — x의 실수 n제곱근.
Example: nthroot(81,4)
`atan2(y,x)` — 점 (x,y)의 각도를 네 사분면을 고려해 구합니다.
Example: atan2(1,1)
`frac(x)` — 소수 부분: x − floor(x).
Example: frac(3.75)
`iPart(x)` — 0을 향해 버린 정수 부분.
Example: iPart(-3.75)
`log(x,b)` — 밑이 b인 로그. b를 생략하면 10을 사용합니다.
Example: log(1000,10)
`ln(x)` — 자연로그.
Example: ln(e)
`exp(x)` — e의 x제곱.
Example: exp(1)
`sinc(x)` — sin(x) / x. x=0이면 1입니다.
Example: sinc(0)
`sinh(x)` — 쌍곡사인.
Example: sinh(1)
`cosh(x)` — 쌍곡코사인.
Example: cosh(0)
`tanh(x)` — 쌍곡탄젠트.
Example: tanh(1)
`asinh(x)` — 역쌍곡사인.
Example: asinh(1)
`acosh(x)` — 역쌍곡코사인. x ≥ 1에서 정의됩니다.
Example: acosh(2)
`atanh(x)` — 역쌍곡탄젠트. |x| < 1에서 정의됩니다.
Example: atanh(0.5)
`gamma(x)` — 감마 함수.
Example: gamma(5)
`erf(x)` — 오차 함수.
Example: erf(1)
`erfc(x)` — 상보 오차 함수: 1 − erf(x).
Example: erfc(1)
`Ei(x)` — 지수 적분 함수.
Example: Ei(1)
`Si(x)` — 사인 적분 함수.
Example: Si(1)
`Ci(x)` — 코사인 적분 함수.
Example: Ci(1)
`zeta(x)` — 리만 제타 함수.
Example: zeta(2)
`factorial(n)` — 음이 아닌 정수 n의 계승 n!.
Example: factorial(5)
`nCr(n,r)` — n개에서 r개를 고르는 조합의 수.
Example: nCr(5,2)
`nPr(n,r)` — n개에서 r개를 순서 있게 고르는 순열의 수.
Example: nPr(5,2)
`gcd(a,b)` — 최대공약수.
Example: gcd(12,18)
`lcm(a,b)` — 최소공배수.
Example: lcm(4,6)
`prime(n)` — n번째 소수를 반환합니다.
Example: prime(1000)
`isprime(n)` — n이 소수이면 true, 아니면 false를 반환합니다. |n| < 2^64인 정수를 지원하며, isprime(2^61-1)은 true입니다.
Example: isprime(97)
`factorint(n)` — 양의 정수를 소인수분해합니다. 별도 10^15 입력 제한은 없으며 계산기 공통 숫자 제한 내에서 실행시간과 취소 기능으로 계산량을 제어합니다.
Example: factorint(360)
`divisors(n)` — 양의 정수 n의 양의 약수를 오름차순으로 반환합니다. 소인수분해는 실행시간으로 제한하며, 출력은 약수 2000개와 40000자까지 허용합니다.
Example: divisors(28)
`rnd()` — [0,1) 구간의 임의 실수.
Example: rnd()
`eng(x)` — 지수가 3의 배수인 공학 표기법.
Example: eng(12345)
`pol(x,y)` — 직교좌표 (x,y)를 극좌표 (r,θ)로 변환합니다.
Example: pol(1,1)
`rec(r,θ)` — 극좌표 (r,θ)를 직교좌표 (x,y)로 변환합니다.
Example: rec(1,0)
`randInt(a,b)` — 양 끝을 포함한 [a,b] 구간의 임의 정수.
Example: randInt(1,6)
`sexagesimal(h,m,s)` — 시·분·초를 십진 각도로 변환합니다.
Example: sexagesimal(1,30,0)
`dms(x)` — 십진 각도를 도·분·초로 변환합니다.
Example: dms(1.5)
`mixed(a,b,c)` — 대분수 a b/c.
Example: mixed(1,1,2)
`quotient(a,b)` — a를 b로 나눈 정수 몫.
Example: quotient(17,5)
`remainder(a,b)` — a를 b로 나눈 나머지.
Example: remainder(17,5)
`mod(a,b)` — a를 b로 나눈 나머지. mod 연산자도 같은 결과를 냅니다.
Example: mod(17,5)
`divmod(a,b)` — a를 b로 나눈 정수 몫과 나머지를 목록으로 반환합니다.
Example: divmod(17,5)
`sumdata(values)` — 값 목록의 합계.
Example: sumdata([1,2,3])
`percent(x)` — x퍼센트 값, x/100.
Example: percent(50)
`degree(x)` — x도를 라디안으로 변환합니다.
Example: degree(30)
`rad(x)` — x를 그대로 반환하고 라디안 값으로 표시합니다.
Example: rad(pi/2)
`gradian(x)` — x그레이드를 라디안으로 변환합니다.
Example: gradian(100)
`fibonacci(n)` — n번째 피보나치 수.
Example: fibonacci(10)
`lucas(n)` — n번째 루카스 수.
Example: lucas(10)
`bernoulli(n)` — n번째 베르누이 수.
Example: bernoulli(4)
`harmonic(n,m)` — 일반화 조화수 H(n,m). m의 기본값은 1입니다.
Example: harmonic(5)
`subfactorial(n)` — n개를 완전히 뒤섞는 경우의 수(교란순열) !n.
Example: subfactorial(5)
`totient(n)` — 오일러 φ(n). n 이하에서 n과 서로소인 정수의 개수입니다.
Example: totient(10)
`divisor_sigma(n,k)` — n의 약수를 각각 k제곱해 더한 값. k의 기본값은 1입니다.
Example: divisor_sigma(12)
`primepi(x)` — x 이하의 소수 개수.
Example: primepi(100)
`nextprime(n)` — n보다 큰 가장 작은 소수.
Example: nextprime(100)
`prevprime(n)` — n보다 작은 가장 큰 소수.
Example: prevprime(100)
`lambertw(x)` — 램버트 W 함수. x·e^x의 역함수입니다.
Example: lambertw(1)
`lambertw(z,k)` — 정수 k로 램버트 W 분기를 선택합니다. 0은 주분기이며 −1 분기도 [−1/e,0)에서 실수입니다.
Example: lambertw(-1,1)
`beta(a,b)` — 베타 함수 B(a,b).
Example: beta(2,3)
`digamma(x)` — 감마 함수의 로그도함수.
Example: digamma(1)
`polygamma(n,x)` — n차 폴리감마 함수.
Example: polygamma(1,1)
`besselj(n,x)` — 제1종 베셀 함수.
Example: besselj(0,1)
`bessely(n,x)` — 제2종 베셀 함수.
Example: bessely(0,1)
`besseli(n,x)` — 제1종 변형 베셀 함수.
Example: besseli(0,1)
`besselk(n,x)` — 제2종 변형 베셀 함수.
Example: besselk(0,1)

## Symbolic
`simplify(expr)` — 수식을 간단히 정리합니다.
Example: simplify(sin(x)^2+cos(x)^2)
`expand(expr)` — 곱과 거듭제곱을 전개합니다.
Example: expand((x+1)^3)
`factor(expr)` — 유리수 범위에서 다항식을 인수분해합니다.
Example: factor(x^2-1)
`collect(expr,x)` — x의 다항식으로 항을 모읍니다.
Example: collect(x^2+2x+1,x)
`subs(expr,x,value)` — x에 value를 대입합니다.
Example: subs(x^2+1,x,3)
`diff(expr,x)` — x에 대한 1계 도함수를 구합니다.
Example: diff(sin(x),x)
`diff(expr,x,n)` — x에 대한 n계 도함수를 구합니다.
Example: diff(x^4,x,2)
`integrate(expr,x)` — 부정적분(원시함수)을 구합니다. Integral이 남으면 기호 적분 알고리즘이 해결하지 못한 것입니다. 정의역 내 유한 구간의 수치 정적분에는 nintegrate(expr,x,a,b)를 사용합니다.
Example: integrate(x^2,x)
`integrate(sqrt(tan(x)),x)` — t=sqrt(tan(x))로 치환해 유리함수를 적분하며 로그·아크탄젠트·C로 결과를 반환합니다. tan(x)>0인 연속 실수 구간에서 유효하며 조건은 Ans에도 보존합니다. 실수 일차식 인수와 4차 이하 근을 가진 적절한 tan/cot 분수 거듭제곱에도 적용됩니다.
Example: integrate(sqrt(tan(x)),x)
`integrate(expr,x,a,b)` — a부터 b까지 정적분을 구합니다.
Example: integrate(x^2,x,0,1)
`limit(expr,x,a)` — x가 a에 다가갈 때 양쪽 극한을 구합니다.
Example: limit(sin(x)/x,x,0)
`limit(expr,x,a,left)` — x가 왼쪽에서 a에 다가갈 때 극한을 구합니다.
Example: limit(1/x,x,0,left)
`limit(expr,x,a,right)` — x가 오른쪽에서 a에 다가갈 때 극한을 구합니다.
Example: limit(1/x,x,0,right)
`series(expr,x,a,n)` — a를 중심으로 n차 항 전까지 급수 전개합니다.
Example: series(exp(x),x,0,6)
`taylor(expr,x,a,n)` — a를 중심으로 n차 테일러 다항식을 구합니다.
Example: taylor(sin(x),x,0,5)
`sum(expr,x,a,b)` — 정수 x가 a부터 b까지일 때 expr의 합을 구합니다.
Example: sum(x^2,x,1,10)
`product(expr,x,a,b)` — 정수 x가 a부터 b까지일 때 expr의 곱을 구합니다.
Example: product(x,x,1,5)
`solve(eq,x)` — 방정식 또는 연립방정식을 x에 대해 풉니다. 복소수 영역에서 먼저 풀고, 절댓값 등 처리하지 못하는 식은 실수 영역으로 자동 재시도하며 결과 안내에 이를 표시합니다. 연립방정식에서는 실수 처리가 필요한 변수만 재시도 중 실수로 취급합니다. 기존 변수 가정도 적용됩니다. ConditionSet은 기호 풀이 미해결을 뜻하며 근의 존재를 보장하지 않습니다. 실수 근의 수치 계산에는 연속이고 양 끝의 부호가 다른 구간에서 nsolve를 사용합니다.
Example: solve(x^2-5x+6=0,x)
`solve(eq,x,real)` — 방정식 하나와 변수 하나에 대해 실수 영역을 명시합니다. complex(복소수)·integer(정수)도 지원하며 기존 변수 가정도 적용됩니다. 영역을 생략하면 절댓값 방정식 등에서 실수 영역으로 자동 재시도합니다.
Example: solve(abs(x-1)=3,x,real)
`solve(abs(x-1)=3,x)` — real 가정 없이도 절댓값 방정식을 풀 수 있습니다.
Example: solve(abs(x-1)=3,x) → {-2, 4}
`solve(exp(x)=x,x)` — 전체 복소수 해 −LambertW(−1,k), k ∈ ℤ를 반환합니다. 실수 영역을 명시하면 EmptySet입니다. 일차식 지수 방정식에는 전체 분기 전략을 적용하며, 그 밖의 Lambert W 방정식에서 보조 solve로 얻은 해는 완전한 해가 아닌 일부 해로 표시합니다.
Example: solve(exp(x)=x,x)
`nsolve(expr,x,a,b)` — [a,b] 구간에서 수치적으로 근을 찾습니다.
Example: nsolve(cos(x)-x,x,0,1)
`nintegrate(expr,x,a,b)` — a부터 b까지 수치 적분합니다.
Example: nintegrate(sin(x),x,0,pi)
`nderivative(expr,x,a)` — x=a에서 수치 미분합니다.
Example: nderivative(sin(x),x,0)
`minimum(expr,x,a,b)` — [a,b] 구간에서 expr의 최솟값을 구합니다.
Example: minimum(x^2,x,-1,2)
`maximum(expr,x,a,b)` — [a,b] 구간에서 expr의 최댓값을 구합니다.
Example: maximum(x^2,x,-1,2)
`piecewise([expr,cond],...)` — 조건별로 값이 다른 조각별 함수를 만듭니다.
Example: piecewise([1,x>0],[0,true])
`apart(expr,x)` — x에 대해 부분분수로 분해합니다.
Example: apart(1/(x*(x+1)),x)
`partfrac(expr,x)` — 부분분수 분해. apart의 별칭입니다.
Example: partfrac(1/(x^2-1),x)
`together(expr)` — 항들을 하나의 분수로 합칩니다.
Example: together(1/x+1/(x+1))
`cancel(expr)` — 유리식의 공통 인수를 약분합니다.
Example: cancel((x^2-1)/(x-1))
`trigsimp(expr)` — 삼각함수 항등식으로 간단히 정리합니다.
Example: trigsimp(sin(x)^2+cos(x)^2)
`trigexpand(expr)` — 합각식과 배각식 등을 전개합니다.
Example: trigexpand(sin(x+y))
`powsimp(expr)` — 밑이 같은 거듭제곱을 합칩니다.
Example: powsimp(x^a*x^b)
`powdenest(expr)` — 중첩 거듭제곱과 근호를 간단히 정리합니다.
Example: powdenest((x^2)^(1/2))
`hyperexpand(expr)` — 초기하함수를 전개합니다.
Example: hyperexpand(exp(x))
`nsimplify(expr)` — 수치값에서 정확한 닫힌 형태를 추정합니다.
Example: nsimplify(0.333333)
`comDenom(expr)` — 분수 합의 공통분모를 구합니다.
Example: comDenom(1/(x+1)+1/(x+2))
`numden(expr)` — 수식의 분자와 분모를 반환합니다.
Example: numden((x+1)/(x-1))
`coeff(expr,x)` — x의 지정된 차수 항의 계수를 구합니다.
Example: coeff(3x^2+2x+1,x)
`quo(a,b,x)` — x에 대한 다항식 a÷b의 몫을 구합니다.
Example: quo(x^3-1,x-1,x)
`rem(a,b,x)` — x에 대한 다항식 a÷b의 나머지를 구합니다.
Example: rem(x^3-1,x-1,x)
`resultant(a,b,x)` — x에 대한 두 다항식의 종결식을 구합니다.
Example: resultant(x^2-1,x-2,x)
`discriminant(poly,x)` — x에 대한 다항식의 판별식을 구합니다.
Example: discriminant(x^2-4x+3,x)
`domain(expr,x)` — x에 대한 수식의 실수 정의역을 구합니다.
Example: domain(1/(x-1),x)
`range(expr,x)` — 정의역에서 수식의 치역을 구합니다.
Example: range(x^2,x)
`roots(poly,x)` — 다항식의 정확한 근과 중복도.
Example: roots(x^2-1,x)
`real_roots(poly,x)` — 다항식의 실근.
Example: real_roots(x^3-1,x)

## Complex
`re(z)` — 복소수의 실수부.
Example: re(3+4i)
`im(z)` — 복소수의 허수부.
Example: im(3+4i)
`conj(z)` — 켤레복소수.
Example: conj(3+4i)
`abs(z)` — 복소수의 절댓값.
Example: abs(3+4i)
`arg(z)` — 복소수의 편각(각도).
Example: arg(1+i)
`polar(r,θ)` — 극형식 r·e^(iθ)의 복소수.
Example: polar(2,pi/3)
`rectpolar(z)` — 복소수의 직교형식과 극형식.
Example: rectpolar(1+i)

## ODE & transforms
`dsolve(eq,y(t),t)` — 상미분방정식을 풉니다.
Example: dsolve(diff(y(t),t)=y(t),y(t),t)
`laplace(f,t,s)` — 시간 변수 t에서 s로 라플라스 변환합니다.
Example: laplace(sin(t),t,s)
`ilaplace(F,s,t)` — s에서 t로 역라플라스 변환합니다.
Example: ilaplace(1/(s^2+1),s,t)
`fourier(f,t,w)` — e^(-2πiwt) 핵을 사용해 t에서 w로 푸리에 변환합니다(보통 주파수).
Example: fourier(exp(-t^2),t,w)
`ifourier(F,w,t)` — w에서 t로 역푸리에 변환합니다.
Example: ifourier(exp(-w^2/4),w,t)
`fft(list)` — 목록의 이산 고속 푸리에 변환.
Example: fft([1,0,0,0])
`ifft(list)` — 목록의 역 이산 고속 푸리에 변환.
Example: ifft([1,1,1,1])
`ztrans(f,n,z)` — 수열 f(n)의 단측 Z변환. n ≥ 0에 대한 f(n)/z^n의 합입니다.
Example: ztrans(a^n,n,z)
`invztrans(F,z,n)` — 유리함수 F(z)의 역 Z변환. 극점에서 복원합니다.
Example: invztrans(z/(z-2),z,n)
`mellin(f,x,s)` — Mellin 변환. 기본 수렴 띠를 결과와 함께 표시합니다.
Example: mellin(exp(-x),x,s)
`invmellin(F,s,x)` — 역 Mellin 변환. 수렴 띠를 두 인자로 직접 지정할 수 있습니다.
Example: invmellin(gamma(s),s,x)
`pdsolve(eq,u(x,y))` — 1계 편미분방정식을 풉니다.
Example: pdsolve(diff(u(x,y),x)+diff(u(x,y),y)=0,u(x,y))
`rsolve(eq,y(n))` — 수열 y(n)에 대한 점화식을 풉니다.
Example: rsolve(y(n)=2*y(n-1),y(n))
`rsolve(eq,y(n),conds)` — 초기조건을 방정식으로 함께 주는 경우입니다.
Example: rsolve(y(n)=y(n-1)+1,y(n),[y(0)=0])

## Vector calculus
`gradient(f,[x,y])` — 스칼라장의 기울기 벡터.
Example: gradient(x^2+y^2,[x,y])
`divergence(f,[x,y])` — 벡터장의 발산.
Example: divergence([x,y],[x,y])
`curl(f,[x,y])` — 2차원 또는 3차원 벡터장의 회전.
Example: curl([-y,x],[x,y])
`hessian(f,[x,y])` — 2계 편도함수의 헤세 행렬.
Example: hessian(x^2*y,[x,y])
`jacobian(f,[x,y])` — 벡터 함수의 야코비 행렬.
Example: jacobian([x*y,x+y],[x,y])
`laplacian(f,[x,y])` — 스칼라장의 라플라시안.
Example: laplacian(x^2+y^2,[x,y])

## Matrix & vector
`det(A)` — 행렬식.
Example: det([[1,2],[3,4]])
`inverse(A)` — 역행렬.
Example: inverse([[1,2],[3,4]])
`transpose(A)` — 전치행렬.
Example: transpose([[1,2],[3,4]])
`rank(A)` — 행렬의 계수.
Example: rank([[1,2],[2,4]])
`trace(A)` — 대각 성분의 합인 대각합.
Example: trace([[1,2],[3,4]])
`ref(A)` — 행 사다리꼴.
Example: ref([[1,2],[3,4]])
`rref(A)` — 기약 행 사다리꼴.
Example: rref([[1,2],[3,4]])
`lu(A)` — 행 순열을 포함한 LU 분해.
Example: lu([[2,1],[1,3]])
`linsolve(A,b)` — 선형계 A·x = b를 풉니다.
Example: linsolve([[2,1],[1,3]],[1,2])
`eigenvalues(A)` — 고유값과 각각의 대수적 중복도를 함께 표시합니다. 재사용되는 수치 결과는 [고유값, 중복도] 쌍의 목록입니다.
Example: eigenvalues([[2,0],[0,3]])
`eigenvectors(A)` — 고유벡터.
Example: eigenvectors([[2,0],[0,3]])
`dot(u,v)` — 내적.
Example: dot([1,2,3],[4,5,6])
`cross(u,v)` — 두 3차원 벡터의 외적.
Example: cross([1,0,0],[0,1,0])
`norm(v)` — 벡터의 유클리드 노름(길이).
Example: norm([3,4])
`normalize(v)` — v 방향의 단위벡터.
Example: normalize([3,4])
`angle(u,v)` — 두 벡터 사이의 각도.
Example: angle([1,0],[0,1])
`projection(u,v)` — u를 v 위로 정사영한 벡터.
Example: projection([1,1],[1,0])
`charpoly(A,x)` — x에 대한 특성다항식.
Example: charpoly([[1,2],[3,4]],x)
`identity(n)` — n×n 단위행렬.
Example: identity(3)
`diag(list)` — 목록의 값을 대각 성분으로 하는 행렬.
Example: diag([1,2,3])
`qr(A)` — QR 분해.
Example: qr([[1,2],[3,4]])
`cholesky(A)` — 촐레스키 분해, A = L·Lᵀ.
Example: cholesky([[4,2],[2,3]])
`nullspace(A)` — 영공간의 기저.
Example: nullspace([[1,2],[2,4]])
`cofactor(A)` — 여인수 행렬.
Example: cofactor([[1,2],[3,4]])
`adjugate(A)` — 수반행렬(고전적 adjoint).
Example: adjugate([[1,2],[3,4]])
`rowspace(A)` — 행공간의 기저.
Example: rowspace([[1,2],[3,4]])
`singularvalues(A)` — 특잇값.
Example: singularvalues([[1,0],[0,2]])
`frob(A)` — 프로베니우스 노름.
Example: frob([[1,2],[3,4]])
`jordan(A)` — 조르당 표준형.
Example: jordan([[2,1],[0,2]])
`dim(v)` — 벡터의 차원 또는 목록의 길이.
Example: dim([1,2,3])
`pinv(A)` — 무어-펜로즈 유사역행렬.
Example: pinv([[1,2],[3,4]])
`ctranspose(A)` — 켤레 전치(에르미트 전치).
Example: ctranspose([[1,2],[3,4]])
`svd(A)` — 특이값 분해 [U, S, V]. 기호 결과가 매우 클 수 있습니다.
Example: svd([[1,0],[0,2]])

## Data & units
`stats(list)` — 목록의 요약 통계량.
Example: stats([1,2,3,4])
`mean(list)` — 산술평균.
Example: mean([1,2,3,4])
`median(list)` — 중앙값.
Example: median([3,1,2])
`variance(list)` — 기본은 표본분산(n−1로 나눔; ddof=1)이며 데이터가 2개 이상이어야 합니다. 두 번째 인수로 ddof=0 또는 1을 지정할 수 있습니다.
Example: variance([1,2,3,4])
`variance(list,0)` — 모집단분산(n으로 나눔)입니다.
Example: variance([1,2,3],0) → 2/3
`stdev(list)` — 기본은 표본표준편차(ddof=1)이며 데이터가 2개 이상이어야 합니다. stats(list)는 모집단·표본 값을 모두 보여 줍니다.
Example: stdev([1,2,3]) → 1
`stdev(list,0)` — 모집단표준편차(ddof=0)입니다.
Example: stdev([2,4,4,4,5,5,7,9],0) → 2
`quartiles(list)` — 포괄적 보간 방식으로 구한 Q1, 중앙값, Q3.
Example: quartiles([1,2,3,4,5])
`sumdata(list)` — 데이터 값의 합계.
Example: sumdata([1,2,3,4])
`regression(data,model)` — 회귀 적합. model은 linear, quadratic, logarithmic, exponential, power 중 하나입니다. 사용자 수식은 `regression(data,custom,수식,독립변수[,시작값])` 형태로 입력합니다. 수식은 y의 우변이며 독립변수를 제외한 기호가 매개변수입니다. 형식: [[매개변수1, 시작값, 하한, 상한], [매개변수2, 시작값, 하한, 상한]]; 상한은 생략할 수 있습니다.
Example: regression([[1,2],[2,4],[3,6]],linear)
Example (y = S(b)/S₀): regression([[0,1],[100,0.9],[200,0.81]],custom,exp(-b*ADC),b)
Example (지수 감쇠): regression([[0,4],[1,2.8],[2,2.1],[3,1.6]],custom,A*exp(-k*x)+C,x)
`covariance(x,y)` — 기본은 표본공분산(n−1로 나눔; ddof=1)이며 데이터 쌍이 2개 이상이어야 합니다. 세 번째 인수로 ddof=0 또는 1을 지정할 수 있습니다.
Example: covariance([1,2,3],[2,4,6])
`covariance(x,y,0)` — 모집단공분산(n으로 나눔)입니다.
Example: covariance([1,2,3],[2,4,6],0) → 4/3
`correlation(x,y)` — 짝을 이룬 두 목록의 상관계수.
Example: correlation([1,2,3],[2,4,6])
`qty(value,unit)` — 단위가 있는 양. 예: qty(2,m).
Example: qty(2,m)+qty(30,cm)
`convert(value,from,to)` — 단위 변환. 예: convert(2,m,cm).
Example: convert(32,degF,degC)

## Distributions

`normpdf(x)` — x에서 표준정규분포의 확률밀도.
Example: normpdf(0)
`normpdf(x,μ,σ)` — 평균 μ, 표준편차 σ인 정규분포의 확률밀도.
Example: normpdf(70,70,10)
`normcdf(x)` — 표준정규분포의 누적확률 P(Z ≤ x).
Example: normcdf(1.96)
`normcdf(low,high)` — 표준정규분포에서 P(low < Z < high). 경계에 -oo와 oo를 사용할 수 있습니다.
Example: normcdf(-1.96,1.96)
`normcdf(low,high,μ,σ)` — 평균 μ, 표준편차 σ인 정규분포의 구간 확률.
Example: normcdf(-oo,60,70,10)
`invnorm(p)` — P(Z ≤ x) = p를 만족하는 표준정규분포의 분위수 x.
Example: invnorm(0.975)
`invnorm(p,μ,σ)` — 평균 μ, 표준편차 σ인 정규분포의 분위수.
Example: invnorm(0.9,70,10)
`tpdf(x,df)` — Student t 분포의 확률밀도.
Example: tpdf(0,10)
`tcdf(x,df)` — 자유도 df인 t 분포에서 P(T ≤ x).
Example: tcdf(2.228,10)
`tcdf(low,high,df)` — t 분포에서 P(low < T < high).
Example: tcdf(-2.228,2.228,10)
`invt(p,df)` — P(T ≤ x) = p를 만족하는 Student t 분포의 분위수 x.
Example: invt(0.975,10)
`chi2pdf(x,df)` — χ² 분포의 확률밀도.
Example: chi2pdf(2,2)
`chi2cdf(x,df)` — 자유도 df인 χ² 분포에서 P(X ≤ x).
Example: chi2cdf(3.8415,1)
`chi2cdf(low,high,df)` — χ² 분포에서 P(low < X < high).
Example: chi2cdf(2,4,3)
`fpdf(x,df1,df2)` — 자유도 df1, df2인 F 분포의 확률밀도.
Example: fpdf(1,2,4)
`fcdf(x,df1,df2)` — F 분포에서 P(F ≤ x).
Example: fcdf(3,2,4)
`fcdf(low,high,df1,df2)` — F 분포에서 P(low < F < high).
Example: fcdf(1,3,2,4)
`binompdf(n,p,k)` — n번 시행하고 성공 확률이 p일 때 이항확률 P(X = k).
Example: binompdf(10,1/2,5)
`binompdf(n,p)` — k=0부터 n까지의 이항확률 목록(n ≤ 100).
Example: binompdf(4,1/2)
`binomcdf(n,p,k)` — 이항분포의 누적확률 P(X ≤ k).
Example: binomcdf(10,1/2,5)
`poissonpdf(μ,k)` — 평균 μ인 포아송분포의 확률 P(X = k).
Example: poissonpdf(2,3)
`poissoncdf(μ,k)` — 포아송분포의 누적확률 P(X ≤ k).
Example: poissoncdf(2,3)
`geometpdf(p,k)` — 기하분포의 확률 P(X = k) = (1−p)^(k−1)·p.
Example: geometpdf(1/2,3)
`geometcdf(p,k)` — 기하분포의 누적확률 P(X ≤ k) = 1 − (1−p)^k.
Example: geometcdf(1/2,3)
`exppdf(x,λ)` — 지수분포의 확률밀도(율 λ, 기본값 1).
Example: exppdf(1)
`expcdf(x,λ)` — 지수분포의 누적확률 P(X ≤ x).
Example: expcdf(1)
`unifpdf(x,a,b)` — [a,b] 구간 균일분포의 확률밀도(기본 구간 [0,1]).
Example: unifpdf(0.5)
`unifcdf(x,a,b)` — 균일분포의 누적확률 P(X ≤ x).
Example: unifcdf(0.5)
`gammapdf(x,k,θ)` — 모양 k, 척도 θ인 감마분포의 확률밀도(θ 기본값 1).
Example: gammapdf(1,1)
`gammacdf(x,k,θ)` — 감마분포의 누적확률 P(X ≤ x).
Example: gammacdf(1,1)
`betapdf(x,α,β)` — 0 ≤ x ≤ 1에서 정의된 베타분포의 확률밀도.
Example: betapdf(0.5,2,3)
`betacdf(x,α,β)` — 베타분포의 누적확률 P(X ≤ x).
Example: betacdf(0.5,2,3)
`lognormpdf(x,μ,σ)` — 로그정규분포의 확률밀도(μ, σ 기본값 0, 1).
Example: lognormpdf(1)
`lognormcdf(x,μ,σ)` — 로그정규분포의 누적확률 P(X ≤ x).
Example: lognormcdf(1)

`hgeompdf(N,K,n,k)` — 초기하 분포의 확률질량. N ≤ 10000, 0 ≤ K,n ≤ N인 정수를 입력합니다.
예시: hgeompdf(10,2,2,2)
`hgeomcdf(N,K,n,k)` — 비복원 추출에서 성공 횟수가 k 이하일 누적확률입니다.
예시: hgeomcdf(10,2,2,1)
`nbinompdf(r,p,k)` — r번째 성공 전까지 실패 횟수 k의 확률질량. k는 0부터 셉니다. 총 시행 수 = k+r. 1 ≤ r ≤ 100000, 0 < p ≤ 1.
예시: nbinompdf(3,0.5,2)
`nbinomcdf(r,p,k)` — r번째 성공 전까지 실패 횟수가 k 이하일 누적확률입니다.
예시: nbinomcdf(3,0.5,2)
`weibullpdf(x,k,λ)` — 형상 k와 척도 λ인 와이블 분포의 밀도. 양수 k,λ를 입력합니다. λ는 발생률이 아니며 생략하면 1입니다.
예시: weibullpdf(3,2,3)
`weibullcdf(x,k,λ)` — 와이블 분포의 누적확률 P(X ≤ x).
예시: weibullcdf(3,2,3)

`cauchypdf(x)` / `cauchypdf(x,x₀,γ)` — 코시 분포의 확률밀도. 기본 위치는 0, 척도는 1입니다. x₀는 유한한 실수, γ는 유한한 양수여야 합니다. 평균과 분산은 정의되지 않습니다.
Example: cauchypdf(0,0,1)
`cauchycdf(x)` / `cauchycdf(x,x₀,γ)` — 코시 분포의 누적확률 P(X ≤ x).
Example: cauchycdf(1,0,1)
`cauchycdf(low,high)` / `cauchycdf(low,high,x₀,γ)` — 코시 분포의 구간 확률. 무한대 경계값도 입력할 수 있습니다.
Example: cauchycdf(-1,1,0,1)
`invcauchy(q)` / `invcauchy(q,x₀,γ)` — 0 ≤ q ≤ 1에 대한 코시 분포의 분위수. q=0과 1에서는 −∞와 ∞, q=0.5에서는 위치(중앙값)를 반환합니다.
Example: invcauchy(0.75,0,1)

## Statistical tests

`ttest(μ0,[...])` — 표본평균을 μ0와 비교하는 단일 표본 t 검정.
Example: ttest(0,[1,2,3,4])
`ttest(μ0,x̄,s,n)` — 요약 통계량으로 수행하는 같은 검정.
Example: ttest(0,2.5,1.291,4)
`ztest(μ0,σ,[...])` — 알려진 표준편차 σ를 사용하는 단일 표본 z 검정.
Example: ztest(0,2,[1,2,3,4])
`ztest(μ0,σ,x̄,n)` — 요약 통계량으로 수행하는 같은 검정.
Example: ztest(0,2,2.5,4)
`chi2test(observed,expected)` — 관측도수와 기대도수를 비교하는 χ² 적합도 검정.
Example: chi2test([10,20,30],[15,20,25])
`anova([...],[...],...)` — 둘 이상의 데이터 목록에 대한 일원분산분석.
Example: anova([1,2,3],[4,5,6])
`tukey([...],[...],...)` — Tukey–Kramer 사후검정. 그룹별 평균 차이와 다중 비교 보정 p값을 구하며 그룹 크기가 달라도 사용할 수 있습니다.
Example: tukey([1,2,3],[4,5,6],[7,8,9])
`ttest2(Δ0,x,y)` — 두 독립 표본의 t 검정(Welch).
Example: ttest2(0,[1,2,3],[2,4,5])
`ttestpaired(Δ0,x,y)` — 대응 표본 t 검정.
Example: ttestpaired(0,[1,2,3],[2,3,5])
`ztest2(Δ0,σx,σy,x,y)` — 표준편차를 아는 두 표본의 z 검정.
Example: ztest2(0,1,1,[1,2,3],[2,4,5])
`chi2independence(x,y[,correction])` — 두 범주 열의 χ² 독립성 검정. 2×2 표의 Yates 연속성 보정은 기본값 1(켬)이며, 0을 넣으면 보정 없는 Pearson χ²를 계산합니다. 다른 크기의 표에는 보정하지 않습니다. 결과에 보정 적용 여부가 표시됩니다.
Example: chi2independence([1,1,2,2],[1,2,1,2])
`fisherexact(x,y)` — 각 열이 두 범주일 때의 피셔 정확 검정.
Example: fisherexact([1,1,1,1,1,1,2,2],[1,1,1,2,2,2,1,2])
`shapiro(list)` — 섀피로-윌크 정규성 검정(값 3~5000개).
Example: shapiro([1,2,3,4,5])
`tinterval(level,[...])` — 평균의 t 신뢰구간. level은 소수(0.95) 또는 백분율(95)입니다.
Example: tinterval(0.95,[1,2,3,4])
`tinterval(level,x̄,s,n)` — 요약 통계량으로 구하는 같은 구간.
Example: tinterval(95,2.5,1.291,4)
`zinterval(level,σ,[...])` — 표준편차 σ를 알고 있을 때의 z 신뢰구간.
Example: zinterval(0.95,2,[1,2,3,4])
`zinterval(level,σ,x̄,n)` — 요약 통계량으로 구하는 같은 구간.
Example: zinterval(95,2,2.5,4)
- 단일 표본 검정은 기본적으로 양측 p값을 반환합니다. 단측 검정에는 left 또는 right를 추가하세요.

## Finance

`tvmfv(n,i,pv,pmt)` — 기간별 이율 i를 적용한 n기간 후 미래가치.
Example: tvmfv(12,0.05/12,-1000,-100)
`tvmpv(n,i,pmt,fv)` — n번 지급액과 최종 가치의 현재가치.
Example: tvmpv(10,0.05,100,0)
`tvmpmt(n,i,pv,fv)` — pv와 fv를 맞추는 기간별 지급액.
Example: tvmpmt(360,0.05/12,250000,0)
`tvmn(i,pv,pmt,fv)` — 기간 수.
Example: tvmn(0.05,0,100,-1000)
`tvmrate(n,pv,pmt,fv)` — 수치적으로 구한 기간별 이율.
Example: tvmrate(10,1000,-150,0)
`npv(rate,[...])` — 현금흐름 목록의 순현재가치. 첫 현금흐름은 시점 0입니다.
Example: npv(0.1,[-1000,300,400,500])
`npv(rate,cf0,[...])` — 초기 현금흐름을 별도로 지정한 순현재가치.
Example: npv(0.1,-1000,[300,400,500])
`irr([...])` — 순현재가치를 0으로 만드는 내부수익률.
Example: irr([-1000,300,400,500])
`irr(cf0,[...])` — 초기 현금흐름을 별도로 지정한 내부수익률.
Example: irr(-1000,[500,500,500])
`amort(i,pv,n)` — 완전 상환 대출의 지급액과 합계. k를 추가하면 k회 지급 후 중지합니다.
Example: amort(0.005,200000,360)
`cagr(start,end,n)` — 시작값에서 끝값까지 n기간의 연평균 성장률.
Example: cagr(1000,2000,5)
- TVM은 받은 돈을 양수, 지급한 돈을 음수로 취급합니다. 매 기간 초에 지급한다면 마지막 인수로 begin을 추가하세요. 기본값은 end입니다. 이율은 지급 주기당 값입니다.


## 확률 모드와 일치하는 분포 함수

이산 CDF는 실수 기준값 이하의 정수 확률질량을 합합니다. 이산 PDF는 정수가 아닌 값에서 0입니다.

확률 모드의 **정규 분포 모수 역산**에서 P(X≤x)=q 또는 P(X≥x)=q와 알려진 모수로 μ 또는 σ를 구할 수 있습니다. q는 0과 1 사이여야 하며, σ는 양수여야 합니다. q=0.5이고 x=μ이면 σ는 하나로 정해지지 않습니다.


## 회귀 추론과 비모수 검정

`regression(data,polynomial,degree)` — 1~10차 다항 최소제곱 회귀.
Example: regression([[0,1],[1,3],[2,9],[3,25],[4,57]],polynomial,3)
`regression(data,multiple)` — 절편을 포함한 다중회귀. 마지막 열은 종속변수, 앞 열은 설명변수(최대 8개)입니다. x,y,z 화면에서는 x,y로 z를 예측하며 식에서는 x1,x2로 표시합니다.
Example: regression([[0,0,1],[1,0,3],[0,1,4],[1,1,7],[2,1,8]],multiple)
`regression(data,logistic)` — 마지막 열이 0/1인 이항 로지스틱 회귀. 확률식, 계수·Odds ratio 신뢰구간, McFadden R², 이탈도, AIC, 우도비 p값을 제공합니다. 완전 분리가 확인되면 Firth 편향 감소를 자동 적용하며, `regression(data,logistic,firth)`는 항상 Firth와 프로파일 페널티 우도 신뢰구간을 사용합니다. 계수 식별이 불가능한 데이터는 거부합니다.
Example: regression([[-3,0],[-2,0],[-1,1],[0,0],[0,1],[1,0],[2,1],[3,1]],logistic)
`wilcoxon(differences)` — 0에 대한 부호순위 검정. 대응 x,y 목록도 받으며 0 차이는 제외합니다. 0이 아닌 차이 50개까지 동률을 포함한 정확 조건부 부호순열, 이후 동률·연속성 보정 정규근사를 사용합니다.
Example: wilcoxon([1,2,3,4,5])
`mannwhitney(x,y)` — 독립 표본 순위 검정. 동률이 없고 작은 표본이 8개 이하, 전체 100개 이하이면 정확 분포를, 나머지는 동률·연속성 보정 정규근사를 사용합니다. 분포 비교 검정이며 위치 차이 해석에는 비슷한 분포 모양이 필요합니다.
Example: mannwhitney([1,2,3],[4,5,6])
`kruskal(group1,group2,...)` — 동률 보정 Kruskal–Wallis H와 카이제곱 근사 p값. 그룹마다 5개 이상의 관측값을 권장합니다.
Example: kruskal([1,2,3,4,5],[4,5,6,7,8],[7,8,9,10,11])

Wilcoxon과 Mann–Whitney는 마지막에 left/right를 추가해 단측 검정을 할 수 있습니다. 기본값은 양측입니다. Wilcoxon은 차이의 대칭성을 가정하며, Mann–Whitney는 첫 표본을 둘째 표본과 비교합니다.

**Column n개**의 **열 수 자동**은 CSV·TSV에서 머리글과 빈 셀을 포함한 가장 넓은 행을 기준으로 열 수(1~100)를 설정합니다. 선택은 저장되며, 해제하면 직접 열 수를 입력할 수 있습니다.

히트맵은 원자료, 행별·열별 Z 점수, 쌍별 Pearson·Spearman·Kendall 상관계수를 지원합니다. 상관관계 모드에서는 x축과 y축에 표시할 변수를 선택하며, 한 축에 추가한 변수는 다른 축에서 자동으로 해제됩니다. 계층 군집을 켜면 단일 연결·유클리드 거리로 행과 열을 재배열하고 덴드로그램을 표시합니다. CSV 머리글이 감지되면 열 이름으로 표시합니다. 빈 셀은 각 상관계수 계산에서 제외하며, 상수 입력이나 완전한 관측값이 2개 미만인 쌍은 —로 표시합니다.

통계 시각화에 **바이올린 + 원자료**와 **히트맵**을 제공합니다. 박스플롯과 바이올린은 **가로**·**세로** 방향을 선택할 수 있으며, 선택한 방향을 저장하고 모든 그룹 패널에 적용합니다. 바이올린은 Gaussian KDE와 Scott 대역폭으로 분포를 추정하고 최대 폭을 동일하게 맞추며, 모든 유한한 원자료 값을 Beeswarm 점으로 겹쳐 표시합니다. 값의 축 위치는 유지하고 그룹 축 방향으로만 이동해 겹침을 피합니다. 점이 밀집한 그룹은 모든 관측값을 그룹 폭 안에 표시하도록 점 크기를 줄입니다. 단일 관측값·상수 그룹은 점만 표시합니다. 원자료 히트맵은 입력 행과 열을 유지하며 처음/마지막 그룹 지정 시 해당 열을 행 이름으로 사용합니다.

통계 회귀 결과에 R²·수정 R²·RMSE·잔차 표준오차, 계수 표준오차·p값·95% t 신뢰구간을 표시합니다. 잔차 진단을 펼치면 적합값 대비 잔차, 표준화 잔차, 레버리지, Cook 거리, Shapiro p, 입력 순서의 Durbin–Watson을 확인할 수 있습니다. 잔차 CSV는 모든 완전한 행을 포함하며 화면 표는 100행을 미리 보여줍니다. 지수·거듭제곱 회귀는 log(y)에서 추론하고 진폭 표준오차는 델타법, 구간은 지수변환을 사용합니다. R²·RMSE·원잔차는 원래 y 척도입니다. 사용자 비선형 회귀는 국소 Jacobian 근사이며 독립·등분산 오차를 가정합니다. 경계 제약 적합은 일반 추론을 생략하고 잔차 자유도가 부족하면 추론값을 제공하지 않습니다. 상수 종속변수의 R²는 정의되지 않습니다. 적합 잔차의 정규성 p값은 탐색용입니다.

Python에서도 `import symvacas_catalog as calc; report = calc.regression_report([[1,2],[2,4],[3,5],[4,4],[5,5],[6,7]], "linear")`로 동일한 추론값과 전체 잔차 딕셔너리를 받습니다. 다항 차수와 사용자 모델·초기값 등 인수는 regression과 같습니다.

통계 화면의 다중 회귀는 x/y/z에서, 로지스틱 회귀는 x/y 또는 x/y/z에서 종속변수 열을 선택합니다. 나머지 열은 설명변수이며, 로지스틱 종속변수에는 0과 1이 모두 필요합니다. 기본값은 마지막 열입니다. 입력 표는 원래 순서를 유지하며 회귀식과 산점도 축에는 선택한 열 이름을 표시합니다. Odds ratio와 OR 95% CI 표기는 언어 설정과 관계없이 영문으로 유지합니다.

로지스틱 회귀에 C-statistic(ROC AUC)와 ROC 그래프(FPR 대 민감도/TPR)를 표시합니다. 동률 점수에는 절반의 점수를 부여하며 대각선은 우연 수준(AUC=0.5) 기준선입니다. 양성은 1이며 ROC/AUC는 적합에 사용한 데이터의 성능을 나타냅니다.

베이지안 회귀: **베이지안 선형 회귀** (`bayeslinear`), **베이지안 로지스틱 회귀** (`bayeslogistic`)를 선택합니다. 두 모델은 종속변수 열 선택, 완전한 행 2개 이상, 최대 5000행·20개 설명변수를 지원합니다. 적절한 사전분포로 공선성·상수 설명변수도 처리합니다. 로지스틱 응답은 0과 1을 모두 포함해야 합니다. 평균 0 사전분포는 중심화·RMS 표준화된 설명변수와 절편에 적용하며, 표시 계수는 원래 단위입니다. 사전 SD 기본값 2.5 (0.000001–1000000), equal-tailed 사후 확률 수준 기본값 0.95 (0과 1 사이)입니다.

`regression(data,bayeslinear,[priorSD,level,shape,scale])`는 beta|sigma² ~ Normal(0,priorSD² sigma² I), sigma² ~ inverse-gamma(shape,scale)를 사용하며 기본값은 [2.5,0.95,2,1]입니다. 계수 주변 사후분포와 새 관측값 예측구간은 정확한 Student-t 분포입니다. 사후 SD와 Student-t scale은 다릅니다. `regression(data,bayeslogistic,[priorSD,level])`는 beta ~ Normal(0,priorSD² I), MAP에서 Gaussian Laplace 사후분포 근사를 사용합니다. 사후 추정값·SD·credible interval·P(beta>0), 로지스틱 오즈비와 ROC/AUC를 표시합니다. 예측·학습 지표는 계수 점추정에서 계산합니다. 구간은 빈도주의 confidence interval과 다른 Bayesian credible interval이며 p값은 제공하지 않습니다.

추론 방법에서 **NUTS**를 선택하면 두 모델의 계수 사후분포를 slice No-U-Turn Sampler (Hoffman과 Gelman, 2014)로 샘플링합니다. 해석적 gradient, 공분산 whitening, 재귀적 트리 확장, U-turn 종료와 warmup 중 dual-averaging 단계 크기 적응(목표 수용 확률 0.8)을 사용합니다. 옵션 뒤에 `[nuts,samples,warmup,maxDepth,seed,chains]`를 추가합니다. 선형 `[2.5,0.95,2,1,[nuts,500,500,8,0,2]]`, 로지스틱 `[2.5,0.95,[nuts,500,500,8,0,2]]`. 기본값: 체인별 유지 표본 500, warmup 500, 최대 트리 깊이 8, seed 0, 체인 2. 범위: 표본 100–5000, warmup 50–5000, 최대 트리 깊이 1–10, seed 0–2147483647, 체인 2–4. U-turn 또는 깊이 제한에서 경로 확장을 종료하며 깊이 D는 반복당 최대 2^D−1 leapfrog 단계를 허용합니다. 초기화·warmup·샘플링 전체의 실제 gradient 평가에 대해 행×계수×평가 횟수는 2억 이하입니다. NUTS는 binary64 계산, 경험적 구간, 고전적 split R-hat·initial-monotone 자기상관 ESS (최대 1000 lag)·MCSE를 사용합니다. 평균 수용 확률·발산·평균 트리 깊이와 leapfrog 단계 수·최대 깊이 도달 횟수·R-hat·ESS를 확인하세요. 최대 깊이 도달, 발산, R-hat>1.05 또는 계산 불가, ESS<100은 경고합니다. 고정 seed로 표본을 재현합니다. 선형 NUTS는 오차 분산을 적분해 제거하며 예측구간은 정확한 켤레 사후분포에서 계산하고 잔차 CSV에도 포함합니다. 저장된 HMC 선택은 표본·warmup·seed·체인 설정을 유지하며 NUTS로 이전됩니다. 기존 leapfrog 횟수는 기본 트리 깊이 8로 대체됩니다. 직접 입력한 `[hmc,...]` 명령은 새 NUTS 문법으로 변경해야 합니다. [알고리즘 참고](https://jmlr.org/papers/v15/hoffman14a.html).

규제 회귀: 선형/다중 회귀와 로지스틱 회귀에서 규제 없음, Ridge, LASSO, Elastic Net을 선택합니다. 설명변수는 평균 0·표준편차 1로 표준화하고 절편에는 규제를 적용하지 않습니다. 표시되는 계수는 원래 단위입니다. α>0, L1 비율은 0~1입니다. 선형 회귀의 목적함수는 SSE/(2n) + α[(1−비율)·||β||²/2 + 비율·||β||₁]이며 로지스틱 회귀는 첫 항 대신 평균 로그 손실을 사용합니다. Ridge는 비율 0, LASSO는 비율 1입니다. 따라서 Ridge의 α는 SSE + α||β||² 형태의 라이브러리와 스케일이 다릅니다.
`regression(data,ridge,alpha)`, `regression(data,lasso,alpha)`, `regression(data,elasticnet,[alpha,l1_ratio])`를 사용합니다. 로지스틱 모델은 `logisticridge`, `logisticlasso`, `logisticelasticnet`이며 종속변수는 0과 1을 모두 포함해야 합니다. α 기본값은 0.1, L1 비율은 0.5입니다. 마지막 열이 종속변수이며 통계 화면에서 다른 열도 선택할 수 있습니다. 최대 5000행·100개 설명변수를 지원하고 결측 행은 제외됩니다. 학습 적합도, 계수, 잔차와 로지스틱 AUC·ROC·로그 손실·정확도를 표시합니다. 일반적인 OLS/Wald 추론은 제공하지 않습니다. 모델 계산은 64비트 부동소수점입니다.
Example: regression([[0,1],[1,3],[2,5],[3,7]],lasso,0.1)
Example: regression([[-2,0],[-1,0],[1,1],[2,1]],logisticelasticnet,[0.1,0.5])
`regression(data,randomforest,[trees,max_depth,seed])` — 랜덤 포레스트 회귀. 기본값은 [100,10,0], 트리 수 1~200, 깊이 1~20, 시드 0~2147483647입니다. 부트스트랩 CART 제곱오차 트리의 예측을 평균하며 노드마다 sqrt(설명변수 수)개 변수를 시도합니다. 선택한 변수가 분할되지 않으면 나머지도 시도합니다. 같은 시드로 결과가 재현됩니다. 학습 R²·RMSE와 OOB R²·RMSE·유효 표본 수, 불순도 기반 변수 중요도, OOB 순열 중요도(변수를 섞은 뒤 R² 감소)를 표시합니다. 순열 중요도는 최대 200개 OOB 관측값에서 3번 반복한 평균이며 음수도 가능합니다. OOB 종속변수가 일정하면 순열 중요도는 계산할 수 없습니다. 트리 모델은 회귀식으로 그래프에 전송할 수 없습니다. x,y 데이터는 예측 곡선을 표시합니다.
Example: regression([[0,0],[1,1],[2,4],[3,9],[4,16]],randomforest,[100,10,0])
Python: `calc.regression_report(data,"elasticnet",[0.1,0.5])` 또는 `calc.regression_report(data,"randomforest",[100,10,0])`로 결과를 받을 수 있습니다.

Random Forest 분류: 유형의 기본값은 자동입니다. 종속변수가 0/1이면 이진 분류로 학습하고, 그 외에는 회귀를 수행합니다. 분류에는 0과 1이 모두 필요하며 양성 클래스는 1입니다. 회귀 또는 이진 분류를 직접 지정할 수도 있습니다. 수식은 `regression(data,randomforestclassifier,[trees,depth,seed])` 또는 `randomforestregressor`를 사용합니다. 분류는 Gini와 동등한 이진 제곱오차 분할을 사용하고, 각 잎의 클래스-1 비율을 트리 전체에서 평균한 예측확률로 ROC/AUC를 계산합니다. C-statistic은 같은 AUC 값입니다. 예측확률 ≥0.5는 클래스 1로 판정합니다. 혼동 행렬은 행=실제 0/1, 열=예측 0/1이며 [[TN,FP],[FN,TP]]입니다. 민감도=TP/(TP+FN), 특이도=TN/(TN+FP), 정확도=(TP+TN)/n을 표시합니다. 학습 지표와 OOB 지표·ROC·혼동 행렬을 따로 제공합니다. OOB 예측이 없는 행은 OOB 계산에서 제외하고, 클래스가 없으면 해당 비율이나 AUC는 계산할 수 없습니다. 분류의 순열 중요도는 OOB AUC 감소량입니다.
Example: regression([[-3,0],[-2,0],[-1,0],[1,1],[2,1],[3,1]],randomforestclassifier,[100,10,0])

규제 로지스틱(Ridge/LASSO/Elastic Net)의 설명변수에도 Odds ratio=exp(β)를 표시합니다. β는 원래 단위의 계수이며 다른 변수를 고정했을 때 해당 변수 1단위 증가에 대한 OR입니다. LASSO로 계수가 0이 된 변수의 OR은 1입니다. 절편은 설명변수 OR로 표시하지 않으며, 규제 추정치에 일반적인 Wald OR 신뢰구간은 제공하지 않습니다.

로지스틱 규제 없음에서 완전 분리를 확인하면 Firth 회귀(log L + 0.5 log|X′WX|)를 자동 적용하고 결과에 Firth를 표시합니다. 셋째 인수로 `firth`를 주면 일반 데이터에도 Firth를 적용합니다. 일반적인 데이터는 기존 MLE를 유지합니다. Firth는 유한한 계수·OR·예측확률·ROC/AUC를 제공하며, 계수와 OR의 95% 신뢰구간은 페널티 프로파일 우도 방식이고 p값은 Wald입니다. MLE의 AIC·우도비 검정은 Firth 결과에서 생략합니다. 모든 종속변수가 같은 경우나 특이한 설계 행렬은 계속 거부합니다. Ridge/LASSO/Elastic Net 선택 시 해당 규제 모델을 유지하며, `cv`는 페널티 α를 5겹 교차검증으로 고릅니다.
로지스틱 잔차 진단 및 전체 CSV에 leverage와 Cook 거리를 포함합니다. hᵢ=wᵢxᵢ′(X′WX)⁻¹xᵢ, Cook Dᵢ=Pearsonᵢ²hᵢ/[p(1−hᵢ)²]인 GLM 1단계 근사를 사용합니다. Firth는 편향 감소 적합점의 Fisher 정보를 사용합니다. 규제 모델은 선택된 변수와 절편으로 만든 설계 행렬에 L2 Hessian을 더한 국소 근사를 사용하며 변수 선택을 고정합니다. 식별 불가능한 활성 변수나 h=1에서는 계산 불가능한 진단값을 비웁니다.

## 고급 통계

통계 화면의 고급 분석에서 보정 방법, 자료 열, 그룹, 설명변수, 검정 옵션을 직접 선택합니다. 현재 데이터·예제·분석 식을 전환할 수 있습니다. 표 분석은 선택한 열의 빈 셀을 자동 삭제하지 않습니다. impute는 빈 셀을 NA로 변환합니다. 모든 고급 분석은 64비트 수치 계산입니다.

`ancova` — 열: 숫자 그룹 ID, 하나 이상의 공변량, 종속변수; 신뢰수준(기본 .95), 기울기 동질성 검정 0/1(기본 1). 일요인·공통 기울기, Type II F 검정, 전체 공변량 평균에서의 조정 평균.
Example: ancova([[1,1,3],[1,2,5],[1,3,4],[1,4,8],[2,2,6],[2,3,7],[2,4,9],[2,5,8],[3,1,5],[3,3,8],[3,4,10],[3,6,11]],0.95,1)

`glm` — 열: 설명변수, 반응변수; 분포 gaussian·binomial(0/1)·poisson·gamma·inversegaussian·nbinom; 연결함수 auto 또는 지원 함수; 고정 NB2 alpha(기본 1); 선택적 오프셋·노출량 목록과 유형. 기본 연결함수는 identity·logit·log·log·log·log. 모형 기반 Wald z 95% 구간; 정규·Gamma·역가우스는 Pearson 분산 추정. NB2 alpha는 추정하지 않고 고정합니다.
Example: glm([[0,2],[1,4],[2,4],[3,7],[4,8],[5,9]],gaussian,auto,1)

`padjust` — p값 목록; 방법 bonferroni / holm / fdr (BH) / by; 유의수준.
Example: padjust([0.01,0.04,0.03,0.2],holm,0.05)

`cohend` — 두 표본; independent(합동 SD) 또는 paired(차이의 SD).
Example: cohend([1,2,4,5],[2,3,5,8],independent)

`eta2` — 독립 그룹별 목록.
Example: eta2([1,2,4,5],[2,3,5,8])

`levene` — 그룹별 목록; 중앙값 기준 등분산 검정.
Example: levene([1,2,4,5],[2,3,5,8])

`bartlett` — 그룹별 목록; 정규성 가정.
Example: bartlett([1,2,4,5],[2,3,5,8])

`mcnemar` — 대응 2×2 빈도표; exact / corrected / asymptotic.
Example: mcnemar([[20,8],[2,15]],exact)

`bayesproportion` — 0/1 목록 또는 [[성공 수,시행 수],...]; Beta 사전 alpha,beta(기본 1,1), 구간 수준, 기준 p0(0~1 사이). 등꼬리 구간·P(p>p0)·다음 성공 확률·BF10(Beta 대립 / p=p0 점귀무).
Example: bayesproportion([1,1,0,1,0,1,1,1,0,1],1,1,0.95,0.5)

`bayesmean` — 분산 미지의 정규 표본; 사전 mu0,kappa0,alpha0,beta0, 구간 수준, 기준값. 분산 ~ InvGamma(alpha0,beta0), 평균|분산 ~ Normal(mu0,분산/kappa0). 기본 0,1,2,1은 자료 척도에 맞춰 조절할 적정 사전분포. 평균의 t 구간과 다음 관측 예측구간.
Example: bayesmean([1,2,3,4,5],0,1,2,1,0.95,0)

`bayesrate` — 횟수 목록(관측당 노출 1) 또는 [[횟수,노출량],...]; Gamma 사전 shape,rate(척도의 역수, 기본 1,1), 구간 수준, 0 이상 기준값. 발생률 등꼬리 구간과 노출 1단위의 예측 횟수 평균·SD.
Example: bayesrate([0,2,1,3,2],1,1,0.95,1)

`kaplanmeier` — 열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.
Example: kaplanmeier([[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]],0.95)

`logrank` — 두 시간/사건 표. 현재 데이터 열: 시간, 사건, 그룹(2개).
Example: logrank([[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]])

`survivalanalysis` — 열: 시간, 사건(0/1), 그룹 ID, 선택적 Cox 설명변수. Cox 0=끔, 1=켬; 이어서 동률 처리와 PH 검정.
Example: survivalanalysis([[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]],0,efron,-1,1)

`cox` — 열: 시간, 사건 0/1, 설명변수. 동률 efron(기본)/breslow, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.
Example: cox([[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]],efron,-1,1)

`repeatedanova` — 행=대상, 열=조건. 둘째 요인 수준: 1=일요인, 2 이상=이요인(첫 요인 최외곽); GG 보정.
Example: repeatedanova([[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]],1)

`mixedmodel` — 열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기(0 없음, 변수 위치, 또는 [1,2]); 셋째 인수 ml(기본)·reml; 최대 5000행. 대상별 BLUP·기울기 상관·singular 진단 포함; 기울기 ICC는 x=0 기준; 점근 Wald z 추론.
Example: mixedmodel([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],0)

`glmm` — 열: 대상 ID, 설명변수, 반응. 랜덤 절편; binomial(0/1, 로짓), poisson·nbinom(NB2, 로그). ML 적응형 Gauss-Hermite 적분: 기본 15점, 1=Laplace, 그 외 7~31점. 선택적 넷째 인수 오프셋 목록, 다섯째 offset·exposure. 최대 1500행·고정계수 8개. 대상별 조건부 효과, 중앙차분 관측 정보행렬·점근 Wald 추론.
Example: glmm([[1,0,0],[1,1,0],[1,2,1],[2,0,0],[2,1,1],[2,2,1],[3,0,0],[3,1,0],[3,2,0],[4,0,1],[4,1,1],[4,2,1],[5,0,1],[5,1,0],[5,2,1],[6,0,0],[6,1,1],[6,2,0]],binomial,15)

`gee` — 열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE. Pearson 분산 보정 상관; AR(1)은 행 순서·등간격 사용. 소수 군집의 Wald 추론은 부정확할 수 있습니다.
Example: gee([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],gaussian,independence)

`multinomial` — 열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준.
Example: multinomial([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])

`ordinal` — 열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓.
Example: ordinal([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])

`poissonreg` — 열: 설명변수, 정수 빈도 반응. 로그 연결함수. 선택적 둘째 인수 행별 오프셋·노출량 목록; 셋째 인수 offset(기본)·exposure(양수, 로그 변환).
Example: poissonreg([[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]])

`nbreg` — 열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정. 선택적 오프셋·노출량 목록과 offset(기본)·exposure 모드.
Example: nbreg([[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]])

`bootstrapci` — 통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식.
Example: bootstrapci([1,2,3,4,5,8],mean,0.95,2000,0)

`testpower` — Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample, 대립가설 two(기본) / greater / less. 정확 noncentral-t 검정력.
Example: testpower(0.5,64,0.05,independent)

`samplesize` — Cohen d, 목표 검정력, 유의수준, 설계, 대립가설. 정확 noncentral-t 검정력.
Example: samplesize(0.5,0.8,0.05,independent)

`kstest` — 두 표본 목록 또는 kstest(data,normal,평균,SD) / kstest(data,uniform,하한,폭). 연속분포 가정; 일표본 p는 근사.
Example: kstest([1,2,4,5],[2,3,5,8])

`crossvalidate` — 열: 설명변수, 반응; 폴드 수, 시드; 분할 random(기본) / blocked / stratified; 모형 linear(기본) / ridge / lasso / elasticnet / logistic; 벌점 alpha 또는 [alpha,l1 비율].
Example: crossvalidate([[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]],3,0)

`pca` — 행=관측, 열=변수; 주성분 수, 표준화 1/0.
Example: pca([[1,2],[2,1],[3,4],[4,3],[5,7]],2,1)

`kmeans` — 숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도.
Example: kmeans([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)

`impute` — 결측값은 NA; mean / median / mode / regression / knn(이웃 수 기본 5). 단일 대체.
Example: impute([[1,NA],[2,4],[NA,6],[4,8]],mean)

베이지안 분석은 독립 관측과 지정한 우도·적정 공액 사전분포를 사용하며 구간은 등꼬리 사후확률 구간입니다. Bayes factor는 가설의 사후확률이 아니며 사전분포에 영향을 받습니다. 계산 근거: [Stanford conjugate priors](https://web.stanford.edu/class/stats200/Lecture21.pdf), [normal-inverse-gamma analysis](https://treese41528.github.io/ComputationalDataScience/Website/part3_bayesian/chapter5/ch5_2-prior-distributions.html).

모형은 수렴하지 않거나 식별 불가능하면 오류를 반환합니다. Cox는 Breslow/Efron 동률, 선택적 좌측 절단, Grambsch–Therneau 스케일된 Schoenfeld 비례위험 검정을 지원하며 순서형 로지스틱은 비례오즈를 가정합니다. 혼합모형은 랜덤 절편과 최대 세 개의 랜덤 기울기(ML·REML)를, GEE는 독립·교환가능·AR(1) 작업상관을 지원합니다. 반복측정 ANOVA는 GG 보정이 포함된 균형 일·이요인 설계를 다룹니다. 단일 대체(mean·median·mode·회귀·k-NN) 후 추론은 대체 불확실성을 반영하지 않습니다. 교차검증은 linear·ridge·lasso·elasticnet·logistic 모형과 random·blocked·stratified 분할을 지원합니다. Firth 추론은 프로파일 페널티 우도 신뢰구간을, 부트스트랩은 백분위 구간을 사용합니다(BCa 없음).

ANCOVA: [partial ANOVA tests](https://www.statsmodels.org/stable/generated/statsmodels.stats.anova.anova_lm.html), [equal slopes](https://itl.nist.gov/div898/software/dataplot/refman1/auxillar/equalslo.htm). GLM: [families, links and dispersion](https://www.statsmodels.org/stable/glm.html).

GLMM: [lme4 adaptive quadrature reference](https://lme4.github.io/lme4/reference/glmer.html).
