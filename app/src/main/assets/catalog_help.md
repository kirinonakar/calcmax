# Catalog function reference

The function catalog inserts ready-to-fill templates into the current editor. Tap a template to insert it, then tap each empty slot (shown as `[]` or as a blank argument such as `round(x,)`) and type its value. In Python mode the same catalog inserts `calc.<function>(...)` and adds the shared `import symvacas_catalog as calc` line and the symbols `x, y, z, t, pi` once at the top of the file.

Use the search box above to filter by function name, template, example or description. The search matches every category at once. Clear the box to see the full reference again.

Tap an example to place its expression in the calculator input.

## Using the catalog
- Numeric trigonometry follows the selected DEG/RAD/GRAD angle unit. An explicit `pi` or `°` inside the expression overrides it.
- Symbolic calculus is always evaluated in radians.
- Matrix and vector commands accept literals such as `[[1,2],[3,4]]`.
- A saved custom function appears in the `Custom` category of the catalog.
- The Functions screen exports the custom library to a JSON file and imports it back; import validates each definition and reports added, replaced and skipped entries.
- Read the hint line under the catalog list for category-specific guidance.

## Scientific
`sin(x)` — Sine of x (uses the current angle unit).
Example: sin(pi/6)
`cos(x)` — Cosine of x (uses the current angle unit).
Example: cos(0)
`tan(x)` — Tangent of x (uses the current angle unit).
Example: tan(pi/4)
`asin(x)` — Inverse sine; result is an angle in the current unit.
Example: asin(1)
`acos(x)` — Inverse cosine; result is an angle in the current unit.
Example: acos(0)
`atan(x)` — Inverse tangent; result is an angle in the current unit.
Example: atan(1)
`abs(x)` — Absolute value, magnitude or complex modulus |x|.
Example: abs(-3)
`floor(x)` — Greatest integer less than or equal to x.
Example: floor(2.7)
`ceil(x)` — Least integer greater than or equal to x.
Example: ceil(2.1)
`round(x,n)` — Round x to n decimal places using banker's rounding (half to even); n defaults to 0.
Example: round(2.5) → 2 (banker's rounding)
`roundh(x,n)` — Round x to n decimal places using round half up (half away from zero); n defaults to 0.
Example: roundh(2.5) → 3 (round half up)
`sign(x)` — Sign of x: −1, 0 or 1.
Example: sign(-5)
`sqrt(x)` — Principal square root.
Example: sqrt(16)
`cbrt(x)` — Cube root.
Example: cbrt(27)
`nthroot(x,n)` — Real n-th root of x.
Example: nthroot(81,4)
`atan2(y,x)` — Angle of the point (x,y) across all four quadrants.
Example: atan2(1,1)
`frac(x)` — Fractional part, x − floor(x).
Example: frac(3.75)
`iPart(x)` — Integer part, truncating toward zero.
Example: iPart(-3.75)
`log(x,b)` — Logarithm of x to base b; base 10 is used when b is omitted.
Example: log(1000,10)
`ln(x)` — Natural logarithm.
Example: ln(e)
`exp(x)` — e raised to the power x.
Example: exp(1)
`sinc(x)` — sin(x) / x, with sinc(0) = 1.
Example: sinc(0)
`sinh(x)` — Hyperbolic sine.
Example: sinh(1)
`cosh(x)` — Hyperbolic cosine.
Example: cosh(0)
`tanh(x)` — Hyperbolic tangent.
Example: tanh(1)
`asinh(x)` — Inverse hyperbolic sine.
Example: asinh(1)
`acosh(x)` — Inverse hyperbolic cosine, defined for x ≥ 1.
Example: acosh(2)
`atanh(x)` — Inverse hyperbolic tangent, defined for |x| < 1.
Example: atanh(0.5)
`gamma(x)` — Gamma function.
Example: gamma(5)
`erf(x)` — Error function.
Example: erf(1)
`erfc(x)` — Complementary error function, 1 − erf(x).
Example: erfc(1)
`Ei(x)` — Exponential integral.
Example: Ei(1)
`Si(x)` — Sine integral.
Example: Si(1)
`Ci(x)` — Cosine integral.
Example: Ci(1)
`zeta(x)` — Riemann zeta function.
Example: zeta(2)
`factorial(n)` — n! for a non-negative integer.
Example: factorial(5)
`nCr(n,r)` — Binomial coefficient, combinations of n taken r at a time.
Example: nCr(5,2)
`nPr(n,r)` — Number of ordered permutations.
Example: nPr(5,2)
`gcd(a,b)` — Greatest common divisor.
Example: gcd(12,18)
`lcm(a,b)` — Least common multiple.
Example: lcm(4,6)
`prime(n)` — Returns the n-th prime number.
Example: prime(1000)
`isprime(n)` — True when n is prime, otherwise false.
Example: isprime(97)
`factorint(n)` — Prime factorisation of an integer.
Example: factorint(360)
`divisors(n)` — All positive divisors of n.
Example: divisors(28)
`rnd()` — Random real number in the interval [0,1).
Example: rnd()
`eng(x)` — Engineering notation with a power-of-three exponent.
Example: eng(12345)
`pol(x,y)` — Polar coordinates (r,θ) from rectangular (x,y).
Example: pol(1,1)
`rec(r,θ)` — Rectangular coordinates (x,y) from polar (r,θ).
Example: rec(1,0)
`randInt(a,b)` — Random integer in the inclusive range [a,b].
Example: randInt(1,6)
`sexagesimal(h,m,s)` — Hours, minutes and seconds converted to decimal degrees.
Example: sexagesimal(1,30,0)
`dms(x)` — Decimal degrees converted to degrees, minutes and seconds.
Example: dms(1.5)
`mixed(a,b,c)` — Mixed fraction a b/c.
Example: mixed(1,1,2)
`quotient(a,b)` — Integer quotient of a divided by b.
Example: quotient(17,5)
`remainder(a,b)` — Remainder of a divided by b.
Example: remainder(17,5)
`mod(a,b)` — Remainder of a divided by b; the mod operator gives the same result.
Example: mod(17,5)
`divmod(a,b)` — Integer quotient and remainder of a divided by b, as a list.
Example: divmod(17,5)
`sumdata(values)` — Sum of a list of values.
Example: sumdata([1,2,3])
`percent(x)` — x percent, x/100.
Example: percent(50)
`degree(x)` — An angle of x degrees converted to radians.
Example: degree(30)
`rad(x)` — Returns x unchanged and marks it as radians.
Example: rad(pi/2)
`gradian(x)` — An angle of x gradians converted to radians.
Example: gradian(100)
`fibonacci(n)` — n-th Fibonacci number.
Example: fibonacci(10)
`lucas(n)` — n-th Lucas number.
Example: lucas(10)
`bernoulli(n)` — n-th Bernoulli number.
Example: bernoulli(4)
`harmonic(n,m)` — Generalized harmonic number H(n,m); m defaults to 1.
Example: harmonic(5)
`subfactorial(n)` — Number of derangements of n items, !n.
Example: subfactorial(5)
`totient(n)` — Euler's totient φ(n).
Example: totient(10)
`divisor_sigma(n,k)` — Sum of the k-th powers of the divisors of n; k defaults to 1.
Example: divisor_sigma(12)
`primepi(x)` — Number of primes less than or equal to x.
Example: primepi(100)
`nextprime(n)` — Smallest prime greater than n.
Example: nextprime(100)
`prevprime(n)` — Largest prime less than n.
Example: prevprime(100)
`lambertw(x)` — Lambert W function, the inverse of x·e^x.
Example: lambertw(1)
`beta(a,b)` — Beta function B(a,b).
Example: beta(2,3)
`digamma(x)` — Logarithmic derivative of the gamma function.
Example: digamma(1)
`polygamma(n,x)` — n-th polygamma function.
Example: polygamma(1,1)
`besselj(n,x)` — Bessel function of the first kind.
Example: besselj(0,1)
`bessely(n,x)` — Bessel function of the second kind.
Example: bessely(0,1)
`besseli(n,x)` — Modified Bessel function of the first kind.
Example: besseli(0,1)
`besselk(n,x)` — Modified Bessel function of the second kind.
Example: besselk(0,1)

## Symbolic
`simplify(expr)` — Simplify an expression.
Example: simplify(sin(x)^2+cos(x)^2)
`expand(expr)` — Expand products and powers.
Example: expand((x+1)^3)
`factor(expr)` — Factor a polynomial over the rationals.
Example: factor(x^2-1)
`collect(expr,x)` — Collect terms as a polynomial in x.
Example: collect(x^2+2x+1,x)
`subs(expr,x,value)` — Substitute value for x.
Example: subs(x^2+1,x,3)
`diff(expr,x)` — First derivative with respect to x.
Example: diff(sin(x),x)
`diff(expr,x,n)` — n-th derivative with respect to x.
Example: diff(x^4,x,2)
`integrate(expr,x)` — Indefinite integral (antiderivative).
Example: integrate(x^2,x)
`integrate(expr,x,a,b)` — Definite integral from a to b.
Example: integrate(x^2,x,0,1)
`limit(expr,x,a)` — Two-sided limit as x tends to a.
Example: limit(sin(x)/x,x,0)
`limit(expr,x,a,left)` — Limit approaching a from the left.
Example: limit(1/x,x,0,left)
`limit(expr,x,a,right)` — Limit approaching a from the right.
Example: limit(1/x,x,0,right)
`series(expr,x,a,n)` — Series expansion about a up to order n.
Example: series(exp(x),x,0,6)
`taylor(expr,x,a,n)` — Taylor polynomial of order n about a.
Example: taylor(sin(x),x,0,5)
`sum(expr,x,a,b)` — Summation of expr over integer x from a to b.
Example: sum(x^2,x,1,10)
`product(expr,x,a,b)` — Product of expr over integer x from a to b.
Example: product(x,x,1,5)
`solve(eq,x)` — Solve an equation or system for x.
Example: solve(x^2-5x+6=0,x)
`nsolve(expr,x,a,b)` — Numeric root search in the interval [a,b].
Example: nsolve(cos(x)-x,x,0,1)
`nintegrate(expr,x,a,b)` — Numeric definite integral from a to b.
Example: nintegrate(sin(x),x,0,pi)
`nderivative(expr,x,a)` — Numeric derivative evaluated at x = a.
Example: nderivative(sin(x),x,0)
`minimum(expr,x,a,b)` — Minimum value of expr on the interval [a,b].
Example: minimum(x^2,x,-1,2)
`maximum(expr,x,a,b)` — Maximum value of expr on the interval [a,b].
Example: maximum(x^2,x,-1,2)
`piecewise([expr,cond],...)` — Piecewise-defined function.
Example: piecewise([1,x>0],[0,true])
`apart(expr,x)` — Partial-fraction decomposition in x.
Example: apart(1/(x*(x+1)),x)
`partfrac(expr,x)` — Partial fractions; alias of apart.
Example: partfrac(1/(x^2-1),x)
`together(expr)` — Combine terms into a single fraction.
Example: together(1/x+1/(x+1))
`cancel(expr)` — Cancel common factors in a rational expression.
Example: cancel((x^2-1)/(x-1))
`trigsimp(expr)` — Simplify using trigonometric identities.
Example: trigsimp(sin(x)^2+cos(x)^2)
`trigexpand(expr)` — Expand trigonometric functions of sums and multiples.
Example: trigexpand(sin(x+y))
`powsimp(expr)` — Combine powers that share a base.
Example: powsimp(x^a*x^b)
`powdenest(expr)` — Simplify nested powers and radicals.
Example: powdenest((x^2)^(1/2))
`hyperexpand(expr)` — Expand hypergeometric functions.
Example: hyperexpand(exp(x))
`nsimplify(expr)` — Guess an exact closed form for a numeric value.
Example: nsimplify(0.333333)
`comDenom(expr)` — Common denominator of a sum of fractions.
Example: comDenom(1/(x+1)+1/(x+2))
`numden(expr)` — Numerator and denominator of an expression.
Example: numden((x+1)/(x-1))
`coeff(expr,x)` — Coefficient of the indicated power of x.
Example: coeff(3x^2+2x+1,x)
`quo(a,b,x)` — Polynomial quotient of a divided by b in x.
Example: quo(x^3-1,x-1,x)
`rem(a,b,x)` — Polynomial remainder of a divided by b in x.
Example: rem(x^3-1,x-1,x)
`resultant(a,b,x)` — Resultant of two polynomials in x.
Example: resultant(x^2-1,x-2,x)
`discriminant(poly,x)` — Discriminant of a polynomial in x.
Example: discriminant(x^2-4x+3,x)
`domain(expr,x)` — Real domain of the expression in x.
Example: domain(1/(x-1),x)
`range(expr,x)` — Range of the expression over its domain.
Example: range(x^2,x)
`roots(poly,x)` — Exact roots of a polynomial with their multiplicities.
Example: roots(x^2-1,x)
`real_roots(poly,x)` — Real roots of a polynomial.
Example: real_roots(x^3-1,x)

## Complex
`re(z)` — Real part of a complex number.
Example: re(3+4i)
`im(z)` — Imaginary part of a complex number.
Example: im(3+4i)
`conj(z)` — Complex conjugate.
Example: conj(3+4i)
`abs(z)` — Modulus (absolute value) of a complex number.
Example: abs(3+4i)
`arg(z)` — Argument (angle) of a complex number.
Example: arg(1+i)
`polar(r,θ)` — Complex number in polar form, r·e^(iθ).
Example: polar(2,pi/3)
`rectpolar(z)` — Rectangular and polar forms of a complex number.
Example: rectpolar(1+i)

## ODE & transforms
`dsolve(eq,y(t),t)` — Solve an ordinary differential equation.
Example: dsolve(diff(y(t),t)=y(t),y(t),t)
`laplace(f,t,s)` — Laplace transform from the time variable t to s.
Example: laplace(sin(t),t,s)
`ilaplace(F,s,t)` — Inverse Laplace transform from s back to t.
Example: ilaplace(1/(s^2+1),s,t)
`fourier(f,t,w)` — Fourier transform from t to w using the e^(-2πiwt) kernel (ordinary frequency).
Example: fourier(exp(-t^2),t,w)
`ifourier(F,w,t)` — Inverse Fourier transform from w back to t.
Example: ifourier(exp(-w^2/4),w,t)
`fft(list)` — Discrete fast Fourier transform of a list.
Example: fft([1,0,0,0])
`ifft(list)` — Inverse discrete fast Fourier transform of a list.
Example: ifft([1,1,1,1])
`ztrans(f,n,z)` — Unilateral Z-transform of the sequence f(n): the sum of f(n)/z^n for n ≥ 0.
Example: ztrans(a^n,n,z)
`invztrans(F,z,n)` — Inverse Z-transform of a rational F(z), reconstructed from its poles.
Example: invztrans(z/(z-2),z,n)
`mellin(f,x,s)` — Mellin transform; its fundamental convergence strip is reported with the result.
Example: mellin(exp(-x),x,s)
`invmellin(F,s,x)` — Inverse Mellin transform; pass the convergence strip as two extra arguments to override the inferred one.
Example: invmellin(gamma(s),s,x)
`pdsolve(eq,u(x,y))` — Solve a first-order partial differential equation.
Example: pdsolve(diff(u(x,y),x)+diff(u(x,y),y)=0,u(x,y))
`rsolve(eq,y(n))` — Solve a recurrence relation for the sequence y(n).
Example: rsolve(y(n)=2*y(n-1),y(n))
`rsolve(eq,y(n),conds)` — The same with initial conditions, given as equations.
Example: rsolve(y(n)=y(n-1)+1,y(n),[y(0)=0])

## Vector calculus
`gradient(f,[x,y])` — Gradient vector of a scalar field.
Example: gradient(x^2+y^2,[x,y])
`divergence(f,[x,y])` — Divergence of a vector field.
Example: divergence([x,y],[x,y])
`curl(f,[x,y])` — Curl of a two- or three-dimensional vector field.
Example: curl([-y,x],[x,y])
`hessian(f,[x,y])` — Hessian matrix of second derivatives.
Example: hessian(x^2*y,[x,y])
`jacobian(f,[x,y])` — Jacobian matrix of a vector function.
Example: jacobian([x*y,x+y],[x,y])
`laplacian(f,[x,y])` — Laplacian of a scalar field.
Example: laplacian(x^2+y^2,[x,y])

## Matrix & vector
`det(A)` — Determinant.
Example: det([[1,2],[3,4]])
`inverse(A)` — Matrix inverse.
Example: inverse([[1,2],[3,4]])
`transpose(A)` — Transpose.
Example: transpose([[1,2],[3,4]])
`rank(A)` — Rank.
Example: rank([[1,2],[2,4]])
`trace(A)` — Trace, the sum of the diagonal entries.
Example: trace([[1,2],[3,4]])
`ref(A)` — Row echelon form.
Example: ref([[1,2],[3,4]])
`rref(A)` — Reduced row echelon form.
Example: rref([[1,2],[3,4]])
`lu(A)` — LU decomposition with row permutations.
Example: lu([[2,1],[1,3]])
`linsolve(A,b)` — Solve the linear system A·x = b.
Example: linsolve([[2,1],[1,3]],[1,2])
`eigenvalues(A)` — Eigenvalues.
Example: eigenvalues([[2,0],[0,3]])
`eigenvectors(A)` — Eigenvectors.
Example: eigenvectors([[2,0],[0,3]])
`dot(u,v)` — Dot product.
Example: dot([1,2,3],[4,5,6])
`cross(u,v)` — Cross product of two three-dimensional vectors.
Example: cross([1,0,0],[0,1,0])
`norm(v)` — Euclidean norm (length) of a vector.
Example: norm([3,4])
`normalize(v)` — Unit vector in the direction of v.
Example: normalize([3,4])
`angle(u,v)` — Angle between two vectors.
Example: angle([1,0],[0,1])
`projection(u,v)` — Projection of u onto v.
Example: projection([1,1],[1,0])
`charpoly(A,x)` — Characteristic polynomial in x.
Example: charpoly([[1,2],[3,4]],x)
`identity(n)` — n×n identity matrix.
Example: identity(3)
`diag(list)` — Diagonal matrix built from a list.
Example: diag([1,2,3])
`qr(A)` — QR decomposition.
Example: qr([[1,2],[3,4]])
`cholesky(A)` — Cholesky decomposition, A = L·Lᵀ.
Example: cholesky([[4,2],[2,3]])
`nullspace(A)` — Basis of the null space.
Example: nullspace([[1,2],[2,4]])
`cofactor(A)` — Matrix of cofactors.
Example: cofactor([[1,2],[3,4]])
`adjugate(A)` — Adjugate (classical adjoint) matrix.
Example: adjugate([[1,2],[3,4]])
`rowspace(A)` — Basis of the row space.
Example: rowspace([[1,2],[3,4]])
`singularvalues(A)` — Singular values.
Example: singularvalues([[1,0],[0,2]])
`frob(A)` — Frobenius norm.
Example: frob([[1,2],[3,4]])
`jordan(A)` — Jordan canonical form.
Example: jordan([[2,1],[0,2]])
`dim(v)` — Dimension of a vector or length of a list.
Example: dim([1,2,3])
`pinv(A)` — Moore-Penrose pseudoinverse.
Example: pinv([[1,2],[3,4]])
`ctranspose(A)` — Conjugate (Hermitian) transpose.
Example: ctranspose([[1,2],[3,4]])
`svd(A)` — Singular value decomposition as [U, S, V]; the symbolic result can be large.
Example: svd([[1,0],[0,2]])

## Data & units
`stats(list)` — Summary statistics of a list.
Example: stats([1,2,3,4])
`mean(list)` — Arithmetic mean.
Example: mean([1,2,3,4])
`median(list)` — Median.
Example: median([3,1,2])
`variance(list)` — Sample variance.
Example: variance([1,2,3,4])
`stdev(list)` — Sample standard deviation.
Example: stdev([1,2,3,4])
`quartiles(list)` — Q1, median and Q3 using inclusive interpolation.
Example: quartiles([1,2,3,4,5])
`sumdata(list)` — Sum of the data values.
Example: sumdata([1,2,3,4])
`regression(data,model)` — Regression fit; model is linear, quadratic, logarithmic, exponential or power. For a custom nonlinear model, use `regression(data,custom,expression,variable[,initials])`, where `expression` is the right-hand side of y. Parameters are all symbols other than the independent variable. Format: [[parameter1, initial, lower, upper], [parameter2, initial, lower, upper]]; upper bound can be omitted.
Example: regression([[1,2],[2,4],[3,6]],linear)
Example (y = S(b)/S₀): regression([[0,1],[100,0.9],[200,0.81]],custom,exp(-b*ADC),b)
Example (exponential decay): regression([[0,4],[1,2.8],[2,2.1],[3,1.6]],custom,A*exp(-k*x)+C,x)
`covariance(x,y)` — Covariance of two paired lists.
Example: covariance([1,2,3],[2,4,6])
`correlation(x,y)` — Correlation coefficient of two paired lists.
Example: correlation([1,2,3],[2,4,6])
`qty(value,unit)` — Quantity with a unit, for example qty(2,m).
Example: qty(2,m)+qty(30,cm)
`convert(value,from,to)` — Unit conversion, for example convert(2,m,cm).
Example: convert(32,degF,degC)

## Distributions

`normpdf(x)` — Standard normal density at x.
Example: normpdf(0)
`normpdf(x,μ,σ)` — Normal density with mean μ and standard deviation σ.
Example: normpdf(70,70,10)
`normcdf(x)` — Standard normal cumulative probability P(Z ≤ x).
Example: normcdf(1.96)
`normcdf(low,high)` — P(low < Z < high) for the standard normal; -oo and oo are accepted bounds.
Example: normcdf(-1.96,1.96)
`normcdf(low,high,μ,σ)` — Normal probability for the interval with mean μ and standard deviation σ.
Example: normcdf(-oo,60,70,10)
`invnorm(p)` — Standard normal quantile: the x with P(Z ≤ x) = p.
Example: invnorm(0.975)
`invnorm(p,μ,σ)` — Quantile of the normal distribution with mean μ and standard deviation σ.
Example: invnorm(0.9,70,10)
`tpdf(x,df)` — Student t density.
Example: tpdf(0,10)
`tcdf(x,df)` — P(T ≤ x) for the t distribution with df degrees of freedom.
Example: tcdf(2.228,10)
`tcdf(low,high,df)` — P(low < T < high).
Example: tcdf(-2.228,2.228,10)
`invt(p,df)` — Student t quantile: the x with P(T ≤ x) = p.
Example: invt(0.975,10)
`chi2pdf(x,df)` — χ² density.
Example: chi2pdf(2,2)
`chi2cdf(x,df)` — P(X ≤ x) for the χ² distribution with df degrees of freedom.
Example: chi2cdf(3.8415,1)
`chi2cdf(low,high,df)` — P(low < X < high).
Example: chi2cdf(2,4,3)
`fpdf(x,df1,df2)` — F density with the two degrees of freedom.
Example: fpdf(1,2,4)
`fcdf(x,df1,df2)` — P(F ≤ x).
Example: fcdf(3,2,4)
`fcdf(low,high,df1,df2)` — P(low < F < high).
Example: fcdf(1,3,2,4)
`binompdf(n,p,k)` — Binomial probability P(X = k) for n trials with success probability p.
Example: binompdf(10,1/2,5)
`binompdf(n,p)` — List of the binomial probabilities for k = 0 to n (n ≤ 100).
Example: binompdf(4,1/2)
`binomcdf(n,p,k)` — Binomial cumulative probability P(X ≤ k).
Example: binomcdf(10,1/2,5)
`poissonpdf(μ,k)` — Poisson probability P(X = k) with mean μ.
Example: poissonpdf(2,3)
`poissoncdf(μ,k)` — Poisson cumulative probability P(X ≤ k).
Example: poissoncdf(2,3)
`geometpdf(p,k)` — Geometric probability P(X = k) = (1−p)^(k−1)·p.
Example: geometpdf(1/2,3)
`geometcdf(p,k)` — Geometric cumulative probability P(X ≤ k) = 1 − (1−p)^k.
Example: geometcdf(1/2,3)
`exppdf(x,λ)` — Exponential density with rate λ; λ defaults to 1.
Example: exppdf(1)
`expcdf(x,λ)` — Exponential cumulative probability P(X ≤ x).
Example: expcdf(1)
`unifpdf(x,a,b)` — Uniform density on [a,b]; the default interval is [0,1].
Example: unifpdf(0.5)
`unifcdf(x,a,b)` — Uniform cumulative probability P(X ≤ x).
Example: unifcdf(0.5)
`gammapdf(x,k,θ)` — Gamma density with shape k and scale θ; θ defaults to 1.
Example: gammapdf(1,1)
`gammacdf(x,k,θ)` — Gamma cumulative probability P(X ≤ x).
Example: gammacdf(1,1)
`betapdf(x,α,β)` — Beta density on 0 ≤ x ≤ 1.
Example: betapdf(0.5,2,3)
`betacdf(x,α,β)` — Beta cumulative probability P(X ≤ x).
Example: betacdf(0.5,2,3)
`lognormpdf(x,μ,σ)` — Log-normal density; μ and σ default to 0 and 1.
Example: lognormpdf(1)
`lognormcdf(x,μ,σ)` — Log-normal cumulative probability P(X ≤ x).
Example: lognormcdf(1)

`hgeompdf(N,K,n,k)` — Hypergeometric probability mass. Use integers N ≤ 10000 and 0 ≤ K,n ≤ N.
Example: hgeompdf(10,2,2,2)
`hgeomcdf(N,K,n,k)` — Cumulative probability of at most k successes in draws without replacement.
Example: hgeomcdf(10,2,2,1)
`nbinompdf(r,p,k)` — Mass for k failures before the r-th success. Failures start at 0; total trials = k+r. Use 1 ≤ r ≤ 100000 and 0 < p ≤ 1.
Example: nbinompdf(3,0.5,2)
`nbinomcdf(r,p,k)` — Cumulative probability of at most k failures before the r-th success.
Example: nbinomcdf(3,0.5,2)
`weibullpdf(x,k,λ)` — Weibull density with positive shape k and scale λ (default 1). λ is a scale, not a rate.
Example: weibullpdf(3,2,3)
`weibullcdf(x,k,λ)` — Weibull cumulative probability P(X ≤ x).
Example: weibullcdf(3,2,3)

`cauchypdf(x)` / `cauchypdf(x,x₀,γ)` — Cauchy density, with default location 0 and scale 1. Require finite x₀ and positive finite γ. The mean and variance are undefined.
Example: cauchypdf(0,0,1)
`cauchycdf(x)` / `cauchycdf(x,x₀,γ)` — Cauchy cumulative probability P(X ≤ x).
Example: cauchycdf(1,0,1)
`cauchycdf(low,high)` / `cauchycdf(low,high,x₀,γ)` — Cauchy interval probability. Infinite bounds are allowed.
Example: cauchycdf(-1,1,0,1)
`invcauchy(q)` / `invcauchy(q,x₀,γ)` — Cauchy quantile for 0 ≤ q ≤ 1. Endpoints return −∞ and ∞; q=0.5 returns the location (median).
Example: invcauchy(0.75,0,1)

## Statistical tests

`ttest(μ0,[...])` — One-sample t test of the sample mean against μ0.
Example: ttest(0,[1,2,3,4])
`ttest(μ0,x̄,s,n)` — The same test from the summary statistics.
Example: ttest(0,2.5,1.291,4)
`ztest(μ0,σ,[...])` — One-sample z test with the known standard deviation σ.
Example: ztest(0,2,[1,2,3,4])
`ztest(μ0,σ,x̄,n)` — The same test from the summary statistics.
Example: ztest(0,2,2.5,4)
`chi2test(observed,expected)` — χ² goodness-of-fit test of observed counts against expected counts.
Example: chi2test([10,20,30],[15,20,25])
`anova([...],[...],...)` — One-way analysis of variance over two or more data lists.
Example: anova([1,2,3],[4,5,6])
`tukey([...],[...],...)` — Tukey–Kramer pairwise mean comparisons with adjusted p values; supports unequal group sizes.
Example: tukey([1,2,3],[4,5,6],[7,8,9])
`ttest2(Δ0,x,y)` — Two-sample t test of two independent samples (Welch).
Example: ttest2(0,[1,2,3],[2,4,5])
`ttestpaired(Δ0,x,y)` — Paired t test on matched rows.
Example: ttestpaired(0,[1,2,3],[2,3,5])
`ztest2(Δ0,σx,σy,x,y)` — Two-sample z test with the known standard deviations.
Example: ztest2(0,1,1,[1,2,3],[2,4,5])
`chi2independence(x,y[,correction])` — χ² test of independence for two category columns. Yates continuity correction defaults to 1 (on) for 2×2 tables; use 0 for uncorrected Pearson χ². Other table sizes are always uncorrected. The result reports whether correction was applied.
Example: chi2independence([1,1,2,2],[1,2,1,2])
`fisherexact(x,y)` — Fisher exact test for two categories in each column.
Example: fisherexact([1,1,1,1,1,1,2,2],[1,1,1,2,2,2,1,2])
`shapiro(list)` — Shapiro-Wilk normality test (3 to 5000 values).
Example: shapiro([1,2,3,4,5])
`tinterval(level,[...])` — t confidence interval for the mean; the level is a fraction (0.95) or a percentage (95).
Example: tinterval(0.95,[1,2,3,4])
`tinterval(level,x̄,s,n)` — The same interval from the summary statistics.
Example: tinterval(95,2.5,1.291,4)
`zinterval(level,σ,[...])` — z confidence interval with the known standard deviation σ.
Example: zinterval(0.95,2,[1,2,3,4])
`zinterval(level,σ,x̄,n)` — The same interval from the summary statistics.
Example: zinterval(95,2,2.5,4)
- One-sample tests return two-tailed p values by default; append left or right for a one-sided test.

## Finance

`tvmfv(n,i,pv,pmt)` — Future value after n periods with the rate i per period.
Example: tvmfv(12,0.05/12,-1000,-100)
`tvmpv(n,i,pmt,fv)` — Present value of n payments and a final value.
Example: tvmpv(10,0.05,100,0)
`tvmpmt(n,i,pv,fv)` — Payment per period that clears pv against fv.
Example: tvmpmt(360,0.05/12,250000,0)
`tvmn(i,pv,pmt,fv)` — Number of periods.
Example: tvmn(0.05,0,100,-1000)
`tvmrate(n,pv,pmt,fv)` — Interest rate per period, found numerically.
Example: tvmrate(10,1000,-150,0)
`npv(rate,[...])` — Net present value of a cash-flow list; the first flow is at time 0.
Example: npv(0.1,[-1000,300,400,500])
`npv(rate,cf0,[...])` — The same with the initial flow given separately.
Example: npv(0.1,-1000,[300,400,500])
`irr([...])` — Internal rate of return that makes the net present value zero.
Example: irr([-1000,300,400,500])
`irr(cf0,[...])` — The same with the initial flow given separately.
Example: irr(-1000,[500,500,500])
`amort(i,pv,n)` — Payment and totals of a fully amortized loan; add k to stop after k payments.
Example: amort(0.005,200000,360)
`cagr(start,end,n)` — Compound annual growth rate from a starting value to an ending value over n periods.
Example: cagr(1000,2000,5)
- TVM values follow the cash-flow convention: money received is positive and money paid is negative. Add begin as the last argument for payments at the beginning of each period; the default is end. Rates are per payment period.


## Distribution functions matching Probability mode

Discrete CDFs include integer masses up to the real threshold; discrete PDFs return 0 for nonintegers.

Probability mode's **Normal parameter solver** finds μ or σ from P(X≤x)=q or P(X≥x)=q and the known parameter. Require 0<q<1 and positive σ. When q=0.5 and x=μ, σ is not uniquely determined.


## Regression inference and rank tests

`regression(data,polynomial,degree)` — Polynomial least squares, degrees 1–10.
Example: regression([[0,1],[1,3],[2,9],[3,25],[4,57]],polynomial,3)
`regression(data,multiple)` — Multiple linear regression with an intercept. Last column is response; preceding columns are predictors (up to eight). The x,y,z workspace uses x and y to predict z; the formula names them x1 and x2.
Example: regression([[0,0,1],[1,0,3],[0,1,4],[1,1,7],[2,1,8]],multiple)
`regression(data,logistic)` — Binomial logistic regression with an intercept and binary 0/1 response in the last column. Reports probability, Wald coefficient/odds-ratio intervals, McFadden R², deviance, AIC and likelihood-ratio p. Separated or singular data are rejected.
Example: regression([[-3,0],[-2,0],[-1,1],[0,0],[0,1],[1,0],[2,1],[3,1]],logistic)
`wilcoxon(differences)` — Signed-rank test against zero; zeros omitted. Also accepts paired x,y lists. Exact conditional sign permutation through 50 nonzero differences (including ties), otherwise tie-corrected normal approximation with continuity correction.
Example: wilcoxon([1,2,3,4,5])
`mannwhitney(x,y)` — Independent rank test. Exact distribution for untied samples with min(nx,ny)≤8 and total n≤100; otherwise tie-corrected normal approximation with continuity correction. Tests distributions; a location interpretation requires comparable distribution shapes.
Example: mannwhitney([1,2,3],[4,5,6])
`kruskal(group1,group2,...)` — Tie-corrected Kruskal–Wallis H and chi-square p. The approximation is more reliable with at least five observations per group.
Example: kruskal([1,2,3,4,5],[4,5,6,7,8],[7,8,9,10,11])

Wilcoxon and Mann–Whitney accept `left` or `right`; default is two-sided. Wilcoxon tests symmetric differences about zero; Mann–Whitney compares the first sample against the second.

Statistics regression results show R², adjusted R², RMSE, residual SE, coefficient SE/p/95% t intervals and expandable residual diagnostics (plot, standardized residuals, leverage, Cook's D, Shapiro p, and Durbin–Watson in input order). Residual CSV includes every complete observation; the on-screen table previews 100 rows. For exponential/power fits inference uses log(y); amplitude SE uses the delta method and its CI is exponentiated. R²/RMSE and raw residuals remain in original y units. Custom nonlinear inference uses the local Jacobian and is approximate. Independent, constant-variance errors are assumed; bounded fits suppress ordinary inference, insufficient residual degrees of freedom leave inference unavailable, and constant responses leave R² undefined. Diagnostic p values for fitted residuals are exploratory.

Python: `import symvacas_catalog as calc; report = calc.regression_report([[1,2],[2,4],[3,5],[4,4],[5,5],[6,7]], "linear")` returns the same inference and full residual dictionary. Arguments match regression, including polynomial degree and custom model/options.

In Statistics mode, multiple regression lets you choose the dependent column among x/y/z; logistic regression supports x/y and x/y/z. The other columns are predictors; the logistic response must contain both 0 and 1. The default is the last column. The input table stays in its original order, and fitted formulas and scatter axes use the chosen column names. Odds ratio and OR 95% CI labels remain English in either language.

Logistic regression also reports C-statistic (ROC AUC) and an ROC graph (FPR versus sensitivity/TPR). Tied scores receive half credit; the diagonal is the chance reference (AUC=0.5). ROC/AUC use the fitted observations with positive class 1 and are apparent training performance.
