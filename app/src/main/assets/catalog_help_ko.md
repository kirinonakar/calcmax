# 카탈로그 함수 설명서

함수 카탈로그는 빈칸이 있는 템플릿을 현재 편집기에 넣습니다. 템플릿을 누른 뒤 `[]` 또는 `round(x,)`처럼 비어 있는 인수를 눌러 값을 입력하세요. Python 모드에서는 `calc.<function>(...)`를 넣고 `import calcmax_catalog as calc` 및 기호 `x, y, z, t, pi`를 파일 위쪽에 한 번 추가합니다.

위 검색창에서 함수 이름, 템플릿, 예제 또는 설명으로 찾을 수 있습니다. 모든 범주를 한꺼번에 검색합니다. 전체 목록을 다시 보려면 검색어를 지우세요.

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
`isprime(n)` — n이 소수이면 true, 아니면 false를 반환합니다.
Example: isprime(97)
`factorint(n)` — 정수의 소인수분해.
Example: factorint(360)
`divisors(n)` — n의 양의 약수를 모두 반환합니다.
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
`integrate(expr,x)` — 부정적분(원시함수)을 구합니다.
Example: integrate(x^2,x)
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
`solve(eq,x)` — 방정식 또는 연립방정식을 x에 대해 풉니다.
Example: solve(x^2-5x+6=0,x)
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
`eigenvalues(A)` — 고유값.
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
`variance(list)` — 표본분산.
Example: variance([1,2,3,4])
`stdev(list)` — 표본표준편차.
Example: stdev([1,2,3,4])
`quartiles(list)` — 포괄적 보간 방식으로 구한 Q1, 중앙값, Q3.
Example: quartiles([1,2,3,4,5])
`sumdata(list)` — 데이터 값의 합계.
Example: sumdata([1,2,3,4])
`regression(data,model)` — 회귀 적합. model은 linear, quadratic, logarithmic, exponential, power 중 하나입니다.
Example: regression([[1,2],[2,4],[3,6]],linear)
`covariance(x,y)` — 짝을 이룬 두 목록의 공분산.
Example: covariance([1,2,3],[2,4,6])
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
`ttest2(Δ0,x,y)` — 두 독립 표본의 t 검정(Welch).
Example: ttest2(0,[1,2,3],[2,4,5])
`ttestpaired(Δ0,x,y)` — 대응 표본 t 검정.
Example: ttestpaired(0,[1,2,3],[2,3,5])
`ztest2(Δ0,σx,σy,x,y)` — 표준편차를 아는 두 표본의 z 검정.
Example: ztest2(0,1,1,[1,2,3],[2,4,5])
`chi2independence(x,y)` — 두 범주 열의 χ² 독립성 검정.
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
