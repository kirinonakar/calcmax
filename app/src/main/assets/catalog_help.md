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

## Step-by-step explanations

Android and Web use the same explanation engine. Explanations retain the computed answer and original domain restrictions; unsupported transformations show a solver summary.

Equation mode always includes explanations. In calculator mode, enable **Calc mode step by step** in Setup to include them on subsequent calculations; this option is off by default.

**Equations:** Linear/quadratic equations, factorable polynomials through degree 8 whose factors are linear or quadratic, cubic Cardano transformations, and power substitutions that reduce to a linear/quadratic equation (such as `x^6-5*x^3+6=0`). Simple trigonometric/exponential/logarithmic equations include inverse and periodic branches. Affine products with exponentials, such as `x*exp(x)=1`, explain the Lambert W inverse and the selected real or complex branches.

**Systems:** Linear systems with at most 6 equations and 6 unknowns, including parameter coefficients/right-hand sides when every pivot and consistency decision is provable. Two-variable polynomial systems with a linear equation support substitution when the remaining equation reduces to degree 2 or less, including matching each root with the other variable (for example, `x+y=3`, `x^2+y^2=5`). Symmetric quadratic systems determining `x^2+y^2` and `x*y` use the squared sum and difference and retain every sign combination. Parameter-dependent rank or consistency falls back to a summary.

Small two-variable polynomial systems with rational coefficients also show a lexicographic elimination basis and back-substitution of each returned solution, with original-equation residual checks. Internal polynomial division is omitted. For example, `solve([x^2+2*y^2=9,x*y=2],[x,y])` shows all four matching pairs. Large or complicated algebraic candidates retain a summary.

**Inequalities:** One-variable polynomial/rational inequalities with rational coefficients (degree at most 6) show factorization, zeros/poles, an exact test point in each interval, and endpoint inclusion. `solve(x^2<4,x)` explains why the solution is `-2<x<2`; denominator zeros are always excluded.

**ODEs:** First-order linear integrating factors with a verified elementary primitive, including nonpolynomial coefficients such as `1/t` and `sin(t)`. Homogeneous second-order equations with constant coefficients include characteristic roots and repeated-root solutions when the root case is decidable.

First-order separable equations show variable separation and formal integrals, with polynomial equilibrium solutions checked before division. Other explicit ODE solutions include a substitution check when their residual simplifies exactly to zero. Verification is distinguished from a full derivation and retains domain restrictions.

**Integration:** Verified SymPy manual-integration rules for the selected variable, including constant parameters. Parameter cases such as `a=0` in `exp(a*x)` and `a=-1` in `x^a` are explained separately. Definite bounds are shown only when the primitive's endpoint difference matches the computed answer. For a worked example, try `integrate(x*exp(x),x)`. Integrals such as `integrate(exp(-x^2)*cos(2*x),x,0,oo)` and `integrate(1/(x^4+1),x)` can produce an answer without a detailed derivation.

**Differentiation:** Sum/product/quotient, constant/variable powers, and common trigonometric/hyperbolic chain rules, through derivative order 10. Traversal is bounded to depth 12, 96 visits and 80 rule steps; omitted detail is reported.

**Limits:** Direct substitution excludes variable exponents and floor/ceiling, sign and piecewise jumps; these use other supported rules or a summary. Cancellation, square-root conjugates, checked `0/0` and infinity/infinity L'Hôpital transformations, highest-power comparison, one-sided cases, bounded local series, and sine/cosine squeeze cases.

These explanations have expression-size, traversal and output-size limits. Unsupported nonlinear systems and ODEs retain summaries; an indefinite integral without a detailed rule can show a derivative check when verified. A missing derivation does not establish that no closed form exists.

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
`isprime(n)` — True when n is prime, otherwise false. Requires an integer with |n| < 2^64; for example, isprime(2^61-1) is true.
Example: isprime(97)
`factorint(n)` — Prime factorisation of a positive integer. No separate 10^15 input cap; execution time and cancellation bound the work, within the calculator's general number limits.
Example: factorint(360)
`divisors(n)` — All positive divisors of a positive integer, sorted ascending. Factorization is bounded by execution time; output is limited to 2000 divisors and 40000 characters.
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
`lambertw(z,k)` — Lambert W branch k (an integer). Branch 0 is the principal branch; branch −1 is also real on [−1/e,0).
Example: lambertw(-1,1)
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
`integrate(expr,x)` — Indefinite integral (antiderivative). An unevaluated Integral means the symbolic algorithm did not finish; use nintegrate(expr,x,a,b) for a definite numeric value on valid finite bounds.
Example: integrate(x^2,x)
`integrate(sqrt(tan(x)),x)` — Uses t=sqrt(tan(x)) to reduce the integral to a rational function, returning logarithms, arctangents and C. Valid on continuous real intervals with tan(x)>0; the conditions are retained in Ans. The same rule covers suitable fractional tan/cot powers with affine real arguments and root order up to 4.
Example: integrate(sqrt(tan(x)),x)
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
`solve(eq,x)` — Solve an equation or system for x. Tries the complex domain first, then automatically retries the real domain for unsupported expressions such as absolute values; the result note identifies real-domain solving. In systems, only variables needed by the real-valued terms are changed for that retry. Variable assumptions still apply. A ConditionSet means the symbolic solution is unresolved, not that a root exists. For a simple unresolved equation in one variable without free parameters, automatically searches −10 to 10 using graph root candidates, then refines and checks candidates against the original expression and domain. Displays at most 16 approximate real roots as partial results; roots can be missed even inside this interval, and complex roots are not searched. Integer-domain solving is excluded. For another interval use nsolve on a continuous interval with a sign change.
Example: solve(x^2-5x+6=0,x)
`solve(eq,x,real)` — Explicitly restrict the domain for one equation and one variable; complex and integer are also supported. Existing assumptions still apply. Omit the domain to enable automatic real-domain retry for absolute-value equations.
Example: solve(abs(x-1)=3,x,real)
`solve(abs(x-1)=3,x)` — Absolute-value equations can be solved without setting a real assumption.
Example: solve(abs(x-1)=3,x) → {-2, 4}
`solve(exp(x)=x,x)` — Returns the entire complex family −LambertW(−1,k), k ∈ ℤ. An explicitly real domain returns EmptySet. Affine exponential equations can use this full-branch strategy; auxiliary solve results for other Lambert W equations are labeled partial rather than complete.

`solve(x*exp(x)=1,x)` — Returns all complex branches LambertW(1,k), k ∈ ℤ, with a step-by-step Lambert W explanation. `solve(x*exp(x)=1,x,real)` returns the unique real root LambertW(1) ≈ 0.5671432904. Finite real numeric coefficients are supported in `(a*x+d)*exp(b*x+c)+f=0` when a and b are nonzero.
Example: solve(exp(x)=x,x)
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

Graphs also accept Desmos-style `{condition:value,condition:value,default}`. The first matching branch wins; omit the default to leave unmatched values undefined. Example: `f(x)={x<0:x^2,x>=0:2*x}`. Append `{condition}` to restrict the whole preceding expression, with or without spaces or outer parentheses: `y=x^2 {0<=x<=2}` and `y=2*x {x>2}`. Chained inequalities are supported; excluded intervals are omitted and explicit polynomial branch boundaries are sampled separately to avoid connecting jumps.
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
`eigenvalues(A)` — Each result shows an eigenvalue and its algebraic multiplicity. The reusable numeric result is a list of [eigenvalue, multiplicity] pairs, not a matrix of eigenvalues.
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

## Stats — choosing a test

```text
What do you want to compare?
├─ Numeric values
│  ├─ One sample vs a target mean → One-sample t test
│  ├─ Two independent groups → Welch t test
│  ├─ Before/after on the same subjects → Paired t test
│  ├─ 3+ independent groups → Welch ANOVA → Games–Howell
│  │  └─ Equal-variance ANOVA selected → Tukey–Kramer
│  ├─ Same subjects in several conditions → Repeated-measures ANOVA / Friedman
│  ├─ Two independent factors → Two-way ANOVA
│  ├─ 3+ factors / numeric covariates → Factorial linear model
│  └─ Groups with covariates to adjust → ANCOVA
├─ Categories / counts
│  ├─ Independent categories → χ² independence
│  │  └─ Sparse 2×2 table → Fisher exact
│  ├─ Paired binary outcomes → McNemar
│  └─ Counts vs expected frequencies → χ² goodness of fit
├─ Time until an event, with censoring → Survival analysis
└─ Predict a response from variables → Regression & models

Review shape, outliers and study design:
  Mean analysis → Shapiro–Wilk + Q–Q plot
  Equal-variance assumption → Brown–Forsythe / Levene
  Independent rank comparisons → Mann–Whitney (2), Kruskal–Wallis (3+)
  Symmetric paired differences → Wilcoxon signed-rank
```

### Independent and paired samples at a glance

| Data structure | Parametric comparison | Nonparametric comparison | Available in this app |
| --- | --- | --- | --- |
| Two independent groups | Student / Welch t-test | Mann–Whitney U | All provided; Welch default |
| Two paired groups | Paired t-test | Wilcoxon signed-rank | Both provided |
| 3+ independent groups | ANOVA / Welch ANOVA | Kruskal–Wallis | All provided; Welch ANOVA default |
| 3+ repeated conditions | Repeated-measures ANOVA | Friedman test | Both provided |

The table compares design-matched families, not interchangeable estimands. Rank comparisons need their own shape/symmetry assumptions for location interpretations. Repeated conditions are measurements of the same subjects, not independent groups.

Choose by purpose, measurement scale and study design first. Do not automatically switch tests based only on a normality p value. Welch t does not require equal variances. One-way ANOVA defaults to Welch with Games–Howell. Choosing equal-variance ANOVA automatically adds Tukey. Rank tests also have assumptions. Use z tests only for known population SDs.

Raw-data t tests, t intervals, ANOVA and Tukey include sample summaries, assumption checks, Q–Q plots and distributions. t tests add effect sizes and two-sided 95% mean intervals; ANOVA adds η² and automatic post-hoc comparisons; Tukey/Games–Howell add overall ANOVA. Paired t checks differences; ANCOVA and factorial linear models check residuals. Summary-only input explicitly reports that normality cannot be checked.

### Parametric methods with normal-model assumptions
- One-sample / paired t tests and t intervals: exact small-sample inference assumes a normal population; paired tests concern the differences. Larger samples may be robust, but inspect skewness and influential outliers.
- Welch t: normal-model mean comparison; equal variances are not required. Student's pooled-variance t additionally requires equal variances. Welch is the default; Student is selectable.
- Welch ANOVA / Games–Howell: normal-model independent mean comparisons without equal variances; default one-way suite.
- Two-way ANOVA / factorial linear models: normal independent errors, common residual variance, identifiable replicated design, and correctly specified interactions. Type II respects marginality; Type III uses sum contrasts for factorial effects.
- Classic ANOVA / Tukey: independent normal errors and equal group variances. ANCOVA adds linear covariate effects and common slopes; repeated-measures ANOVA uses within-subject assumptions and sphericity corrections.
- Gaussian mixed models: normal conditional errors and random effects, not necessarily a normal raw pooled response. Bayesian mean / two-sample comparisons assume their specified normal likelihood.
- Bartlett: a variance test that is sensitive to non-normality; prefer median-centered Levene / Brown–Forsythe when normality is doubtful.

### Parametric models do not all require normal observations
- z tests / z intervals require known population SDs and a normal or suitably approximated sampling distribution of the mean; a sample SD alone is insufficient.
- Binomial logistic, multinomial and ordinal models use categorical likelihoods; Poisson / negative-binomial models use count likelihoods. Check the chosen family, link, dispersion, design and model diagnostics instead of demanding normal outcomes.
- GEE uses a mean/variance model and working correlation for clusters; it does not require normal raw outcomes. A robust covariance does not resolve very few clusters or a misspecified mean model.
- GLMM assumptions follow its selected family and random effects. Bayesian proportion / Poisson rate models use binomial / Poisson likelihoods.

### Nonparametric methods: no normality requirement
- Mann–Whitney U: two independent distributions. Kruskal–Wallis: several independent distributions. A median/location interpretation needs comparable shapes; neither is simply a drop-in test of means.
- Friedman: three or more repeated conditions, ranked within independent subjects; complete matched data and a chi-square approximation with tie correction.
- Wilcoxon signed-rank: paired or one-sample differences, with symmetry for a location interpretation. Strong asymmetry is not fixed by choosing a rank test.
- Kolmogorov–Smirnov: compare continuous distributions. A one-sample reference distribution must be fully specified independently; fitting its parameters from the same sample invalidates the usual p value (no Lilliefors correction here).
- Kaplan–Meier / log-rank: censored event times without normality, but censoring and study-design assumptions still matter. Cox is semiparametric and requires proportional hazards, not normal outcomes.
- Bootstrap: no normality assumption, but this app's IID resampling requires independent, representative observations; use an appropriate design for paired, clustered or time-dependent data.

### Categorical tests: normality is not the decision criterion
- χ² independence / goodness of fit: independent counts and adequate expected frequencies. Fisher exact: sparse independent 2×2 tables. McNemar: paired binary outcomes. Choose by design and counts, not Shapiro p values.

### How to choose
- Start with the question: mean difference, distribution difference, association, prediction or survival. Then identify independent groups, paired observations or clusters.
- For mean questions, use the t/ANOVA family when its error model and design are reasonable. Inspect Q–Q plots, sample sizes, skewness and outliers together. Use Welch for independent two-group means with unequal variances.
- For ordinal/rank or distribution questions, consider Mann–Whitney or Kruskal–Wallis; for paired symmetric location differences, consider Wilcoxon. State the changed estimand rather than calling every rank result a mean or median difference.
- For severely non-normal, asymmetric or dependent data, review transformations, an appropriate distribution/cluster model or design-aware inference; a nonparametric label alone is not a remedy.
- Shapiro p ≥ 0.05 does not prove normality; p < 0.05 does not automatically invalidate every mean analysis. Do not let a preliminary significance test silently choose the main method.

## Data & units
`stats(list)` — Summary statistics of a list.
Example: stats([1,2,3,4])
`mean(list)` — Arithmetic mean.
Example: mean([1,2,3,4])
`median(list)` — Median.
Example: median([3,1,2])
`variance(list)` — Sample variance by default (division by n−1; ddof=1), requiring at least two values. An optional second argument selects ddof=0 or 1.
Example: variance([1,2,3,4])
`variance(list,0)` — Population variance (division by n).
Example: variance([1,2,3],0) → 2/3
`stdev(list)` — Sample standard deviation by default (ddof=1), requiring at least two values. stats(list) shows both population and sample values.
Example: stdev([1,2,3]) → 1
`stdev(list,0)` — Population standard deviation (ddof=0).
Example: stdev([2,4,4,4,5,5,7,9],0) → 2
`quartiles(list)` — Q1, median and Q3 using inclusive interpolation.
Example: quartiles([1,2,3,4,5])
`sumdata(list)` — Sum of the data values.
Example: sumdata([1,2,3,4])
`regression(data,model)` — Regression fit; model is linear, quadratic, logarithmic, exponential or power. For a custom nonlinear model, use `regression(data,custom,expression,variable[,initials])`, where `expression` is the right-hand side of y. Parameters are all symbols other than the independent variable. Format: [[parameter1, initial, lower, upper], [parameter2, initial, lower, upper]]; upper bound can be omitted.
Example: regression([[1,2],[2,4],[3,6]],linear)
Example (y = S(b)/S₀): regression([[0,1],[100,0.9],[200,0.81]],custom,exp(-b*ADC),b)
Example (exponential decay): regression([[0,4],[1,2.8],[2,2.1],[3,1.6]],custom,A*exp(-k*x)+C,x)
`covariance(x,y)` — Sample covariance by default (division by n−1; ddof=1), requiring at least two pairs. An optional third argument selects ddof=0 or 1.
Example: covariance([1,2,3],[2,4,6])
`covariance(x,y,0)` — Population covariance (division by n).
Example: covariance([1,2,3],[2,4,6],0) → 4/3
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

`ttest(μ0,[...])` — Compare one sample mean with a target, such as average score against 70. One-sample t test of the sample mean against μ0.
Example: ttest(0,[1,2,3,4])
`ttest(μ0,x̄,s,n)` — Compare one sample mean with a target, such as average score against 70. The same test from the summary statistics.
Example: ttest(0,2.5,1.291,4)
`ztest(μ0,σ,[...])` — Compare a mean with a target only when population SD is known. One-sample z test with the known standard deviation σ.
Example: ztest(0,2,[1,2,3,4])
`ztest(μ0,σ,x̄,n)` — Compare a mean with a target only when population SD is known. The same test from the summary statistics.
Example: ztest(0,2,2.5,4)
`chi2test(observed,expected)` — Compare observed category frequencies with specified expected frequencies. χ² goodness-of-fit test of observed counts against expected counts.
Example: chi2test([10,20,30],[15,20,25])
`anova([...],[...],...)` — Compare means across independent groups; review normal errors and equal variances. One-way analysis of variance over two or more data lists.
Example: anova([1,2,3],[4,5,6])
`ttest2(delta, A, B, student)` — Compare means of two unrelated groups; Welch allows unequal variances. Pooled Student t; the three-argument default remains Welch.

`welchanova(A, B, ...)` — Compare independent group means with unequal variances; automatically includes Games–Howell comparisons. Unequal-variance one-way ANOVA with automatic Games–Howell.

`gameshowell(A, B, ...)` — Compare each pair of independent group means without equal variances, with adjusted p values and simultaneous intervals. Pairwise unequal-variance comparisons and simultaneous 95% intervals.

`tukey([...],[...],...)` — Identify which group means differ after ANOVA, with familywise multiplicity correction. Tukey–Kramer pairwise mean comparisons with adjusted p values; supports unequal group sizes.
Example: tukey([1,2,3],[4,5,6],[7,8,9])
`ttest2(Δ0,x,y)` — Compare means of two unrelated groups; Welch allows unequal variances. Two-sample t test of two independent samples (Welch).
Example: ttest2(0,[1,2,3],[2,4,5])
`ttestpaired(Δ0,x,y)` — Compare before/after measurements or matched pairs; analyze A−B differences. Paired t test on matched rows.
Example: ttestpaired(0,[1,2,3],[2,3,5])
`ztest2(Δ0,σx,σy,x,y)` — Compare two independent means when both population SDs are known. Two-sample z test with the known standard deviations.
Example: ztest2(0,1,1,[1,2,3],[2,4,5])
`chi2independence(x,y[,correction])` — Test association between two independent categorical variables. χ² test of independence for two category columns. Yates continuity correction defaults to 1 (on) for 2×2 tables; use 0 for uncorrected Pearson χ². Other table sizes are always uncorrected. The result reports whether correction was applied.
Example: chi2independence([1,1,2,2],[1,2,1,2])
`fisherexact(x,y)` — Test association in a 2×2 table, especially with small expected counts. Fisher exact test for two categories in each column.
Example: fisherexact([1,1,1,1,1,1,2,2],[1,1,1,2,2,2,1,2])
`shapiro(list)` — Check evidence against normality; interpret with Q–Q plots, not as a pass/fail gate. Shapiro-Wilk normality test (3 to 5000 values).
Example: shapiro([1,2,3,4,5])
`tinterval(level,[...])` — Estimate a mean with uncertainty when population SD is unknown. t confidence interval for the mean; the level is a fraction (0.95) or a percentage (95).
Example: tinterval(0.95,[1,2,3,4])
`tinterval(level,x̄,s,n)` — Estimate a mean with uncertainty when population SD is unknown. The same interval from the summary statistics.
Example: tinterval(95,2.5,1.291,4)
`zinterval(level,σ,[...])` — Estimate a mean interval when population SD is known. z confidence interval with the known standard deviation σ.
Example: zinterval(0.95,2,[1,2,3,4])
`zinterval(level,σ,x̄,n)` — Estimate a mean interval when population SD is known. The same interval from the summary statistics.
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
`regression(data,logistic)` — Binomial logistic regression with an intercept and binary 0/1 response in the last column. Reports probability, coefficient/odds-ratio intervals, McFadden R², deviance, AIC and likelihood-ratio p. Complete separation automatically triggers Firth bias reduction; `regression(data,logistic,firth)` always applies Firth with profile penalized-likelihood intervals. Singular designs are rejected.
Example: regression([[-3,0],[-2,0],[-1,1],[0,0],[0,1],[1,0],[2,1],[3,1]],logistic)
`wilcoxon(differences)` — Compare paired differences using ranks when a symmetric location-shift model is appropriate. Signed-rank test against zero; zeros omitted. Also accepts paired x,y lists. Exact conditional sign permutation through 50 nonzero differences (including ties), otherwise tie-corrected normal approximation with continuity correction.
Example: wilcoxon([1,2,3,4,5])
`mannwhitney(x,y)` — Compare distributions of two independent groups using ranks; a median interpretation needs similar shapes. Independent rank test. Exact distribution for untied samples with min(nx,ny)≤8 and total n≤100; otherwise tie-corrected normal approximation with continuity correction. Tests distributions; a location interpretation requires comparable distribution shapes.
Example: mannwhitney([1,2,3],[4,5,6])
`kruskal(group1,group2,...)` — Compare distributions of independent groups using ranks; normality is not required. Tie-corrected Kruskal–Wallis H and chi-square p. The approximation is more reliable with at least five observations per group.
Example: kruskal([1,2,3,4,5],[4,5,6,7,8],[7,8,9,10,11])

Wilcoxon and Mann–Whitney accept `left` or `right`; default is two-sided. Wilcoxon tests symmetric differences about zero; Mann–Whitney compares the first sample against the second.

In **n columns** mode, **Auto columns** sets the count from the widest CSV/TSV row, including headers and blank cells (1–100 columns). The option is saved. Turn it off to set the count manually.

Heat maps support raw values, row-wise and column-wise z scores, and pairwise Pearson, Spearman, or Kendall correlation. Correlation mode lets you select variables for the x and y axes; selecting a variable on one axis removes it from the other. Optional hierarchical clustering reorders rows and columns and draws dendrograms. Detected CSV headers label the columns. Missing cells are excluded from each pair; constant inputs and pairs with fewer than two complete observations show —.

Statistics visualization includes **Violin + points** and **Heat map**. Box and violin plots support **Horizontal** and **Vertical** orientation; the selected orientation is saved and applies to all grouped panels. Violin plots use Gaussian KDE with Scott's bandwidth, scaled to equal maximum width, with every finite observation overlaid as a Beeswarm at its original value. Only the category-axis position changes to avoid overlaps; dense groups use smaller markers to fit all observations within the group width. Singletons and constant groups show points only. Raw heat maps preserve input rows and columns; first/last grouping uses that column as row labels.

Statistics regression results show R², adjusted R², RMSE, residual SE, coefficient SE/p/95% t intervals and expandable residual diagnostics (plot, standardized residuals, leverage, Cook's D, Shapiro p, and Durbin–Watson in input order). Residual CSV includes every complete observation; the on-screen table previews 100 rows. For exponential/power fits inference uses log(y); amplitude SE uses the delta method and its CI is exponentiated. R²/RMSE and raw residuals remain in original y units. Custom nonlinear inference uses the local Jacobian and is approximate. Independent, constant-variance errors are assumed; bounded fits suppress ordinary inference, insufficient residual degrees of freedom leave inference unavailable, and constant responses leave R² undefined. Diagnostic p values for fitted residuals are exploratory.

Python: `import symvacas_catalog as calc; report = calc.regression_report([[1,2],[2,4],[3,5],[4,4],[5,5],[6,7]], "linear")` returns the same inference and full residual dictionary. Arguments match regression, including polynomial degree and custom model/options.

In Statistics mode, multiple regression lets you choose the dependent column among x/y/z; logistic regression supports x/y and x/y/z. The other columns are predictors; the logistic response must contain both 0 and 1. The default is the last column. The input table stays in its original order, and fitted formulas and scatter axes use the chosen column names. Odds ratio and OR 95% CI labels remain English in either language.

Logistic regression also reports C-statistic (ROC AUC) and an ROC graph (FPR versus sensitivity/TPR). Tied scores receive half credit; the diagonal is the chance reference (AUC=0.5). ROC/AUC use the fitted observations with positive class 1 and are apparent training performance.

Bayesian regression: select **Bayesian linear regression** (`bayeslinear`) or **Bayesian logistic regression** (`bayeslogistic`). Both support any dependent column, at least 2 complete rows, up to 5000 observations and 20 predictors, including collinear/constant predictors with proper priors. The logistic response must contain both 0 and 1. Priors are zero-mean on centered, RMS-standardized predictors and include the intercept; reported coefficients are in original units. Prior SD defaults to 2.5 (range 0.000001–1000000); equal-tailed credible level defaults to 0.95 (strictly between 0 and 1).

`regression(data,bayeslinear,[priorSD,level,shape,scale])` uses beta|sigma² ~ Normal(0, priorSD² sigma² I), sigma² ~ inverse-gamma(shape,scale); defaults [2.5,0.95,2,1]. Coefficient marginal posteriors and new-observation predictive intervals are exact Student-t distributions. Posterior SD differs from the Student-t scale. `regression(data,bayeslogistic,[priorSD,level])` uses beta ~ Normal(0,priorSD² I) with a Gaussian Laplace approximation at the MAP. Results show posterior estimates/SD, credible intervals, P(beta>0), logistic odds ratios and ROC/AUC. Predictions and training metrics evaluate the coefficient point estimate, rather than averaging predictions over the posterior. These intervals are Bayesian credible intervals, not frequentist confidence intervals; p values are not reported.

Choose **NUTS** under Inference method to sample either full coefficient posterior using the slice No-U-Turn Sampler (Hoffman and Gelman, 2014), analytic gradients, covariance whitening, recursive tree doubling, U-turn termination and warmup-only dual-averaging step size adaptation (target acceptance probability 0.8). Append `[nuts,samples,warmup,maxDepth,seed,chains]` to the prior options: linear `[2.5,0.95,2,1,[nuts,500,500,8,0,2]]`, logistic `[2.5,0.95,[nuts,500,500,8,0,2]]`. Defaults: 500 retained draws per chain, 500 warmup, maximum tree depth 8, seed 0, 2 chains. Ranges: samples 100–5000, warmup 50–5000, maximum tree depth 1–10, seed 0–2147483647, chains 2–4. Trajectories stop at a U-turn or the depth limit; depth D permits at most 2^D−1 leapfrog steps per iteration. Actual gradient evaluations across initialization, warmup and sampling are capped at 200 million rows × coefficients × evaluations. NUTS uses binary64 arithmetic, empirical intervals, classical split R-hat and initial-monotone autocorrelation ESS (up to 1000 lags), and MCSE. Inspect mean acceptance probability, divergences, mean tree depth/leapfrog steps and depth-limit hits, R-hat and ESS; warnings flag depth-limit hits, divergences, R-hat above 1.05/unavailable or ESS below 100. A fixed seed reproduces draws. Linear NUTS integrates out error variance and retains exact conjugate predictive intervals; these are included in residual CSV. Saved HMC selections migrate to NUTS, preserving sample/warmup/seed/chain settings; the old leapfrog count is replaced with default tree depth 8. Previously entered `[hmc,...]` commands must use the new NUTS syntax. [Algorithm reference](https://jmlr.org/papers/v15/hoffman14a.html).

Regularized regression: choose None, Ridge, LASSO, or Elastic Net under linear/multiple or logistic regression. Predictors are standardized to zero mean and unit population SD; the intercept is unpenalized and displayed coefficients use original units. Alpha must be positive and L1 ratio is 0–1. The linear objective is SSE/(2n) + alpha[(1−ratio)||beta||²/2 + ratio||beta||₁]; logistic uses mean log loss instead of SSE/(2n). Ridge uses ratio 0 and LASSO uses ratio 1. Ridge alpha therefore differs in scale from libraries minimizing SSE + alpha||beta||².
Use `regression(data,ridge,alpha)`, `regression(data,lasso,alpha)`, or `regression(data,elasticnet,[alpha,l1_ratio])`. Logistic modes are `logisticridge`, `logisticlasso`, and `logisticelasticnet`; the response must contain both 0 and 1. Defaults: alpha 0.1, L1 ratio 0.5. The last column is response; Statistics allows selecting another column. Up to 5000 rows and 100 predictors are supported, with incomplete rows excluded by the workspace. Results include training fit, coefficients, residuals, and logistic AUC/ROC/log loss/accuracy. Ordinary OLS/Wald inference is unavailable. These models use 64-bit floating point arithmetic.
Example: regression([[0,1],[1,3],[2,5],[3,7]],lasso,0.1)
Example: regression([[-2,0],[-1,0],[1,1],[2,1]],logisticelasticnet,[0.1,0.5])
`regression(data,randomforest,[trees,max_depth,seed])` — Random Forest regression. Defaults [100,10,0]; 1–200 trees, depth 1–20, seed 0–2147483647. Averages bootstrap CART squared-error trees, trying sqrt(predictor count) features per node and additional features if sampled features cannot split. Fixed seeds reproduce results. Reports training R²/RMSE, OOB R²/RMSE and coverage, impurity feature importance, and OOB permutation importance (decrease in R²). Permutation importance averages three shuffles on at most 200 OOB observations and may be negative; a constant OOB response leaves it undefined. Forests cannot be transferred as an algebraic graph expression; x,y data displays a prediction curve.
Example: regression([[0,0],[1,1],[2,4],[3,9],[4,16]],randomforest,[100,10,0])
Python: `calc.regression_report(data,"elasticnet",[0.1,0.5])` or `calc.regression_report(data,"randomforest",[100,10,0])` returns the report dictionary.

Random Forest classification: the default task is Auto. A 0/1 response selects binary classification; other responses select regression. Classification requires both classes, with positive class 1. You can also select Regression or Binary classification explicitly, or use `regression(data,randomforestclassifier,[trees,depth,seed])` / `randomforestregressor`. Binary squared-error splits are equivalent to Gini splits; probabilities average the class-1 fraction in each tree leaf. ROC/AUC uses these probabilities, and C-statistic equals AUC. Probability ≥0.5 predicts class 1. Confusion matrix rows are observed 0/1 and columns predicted 0/1: [[TN,FP],[FN,TP]]. Sensitivity=TP/(TP+FN), specificity=TN/(TN+FP), accuracy=(TP+TN)/n. Training and OOB metrics, ROC curves, and confusion matrices are separate. Rows without OOB predictions are excluded from OOB evaluation, and absent classes leave the corresponding ratios/AUC unavailable. Classification permutation importance measures decrease in OOB AUC.
Example: regression([[-3,0],[-2,0],[-1,0],[1,1],[2,1],[3,1]],randomforestclassifier,[100,10,0])

Regularized logistic regression (Ridge/LASSO/Elastic Net) also reports predictor Odds ratio=exp(beta), using original-unit coefficients for a one-unit predictor increase with other predictors held fixed. A coefficient shrunk to zero by LASSO has OR=1. The intercept is not shown as a predictor OR. Ordinary Wald OR confidence intervals are unavailable for penalized estimates.

Unregularized logistic regression automatically applies Firth bias reduction (log L + 0.5 log|X′WX|) when a strictly separating coefficient vector certifies complete separation; ordinary datasets retain MLE. Passing `firth` as the third argument applies the same estimator to ordinary datasets. Results explicitly identify Firth and include finite coefficients, OR, probabilities and ROC/AUC. Coefficient/OR 95% intervals come from the profile penalized likelihood; p values remain Wald. Ordinary MLE AIC and likelihood-ratio tests are omitted for Firth fits. Constant responses and singular designs remain invalid; Ridge/LASSO/Elastic Net keep their selected penalty, and `cv` selects the penalty alpha by five-fold cross-validation.
Logistic residual diagnostics and full CSV include leverage and Cook's distance. The one-step GLM approximation uses hᵢ=wᵢxᵢ′(X′WX)⁻¹xᵢ and Cook Dᵢ=Pearsonᵢ²hᵢ/[p(1−hᵢ)²]. Firth uses Fisher information at the bias-reduced fit. Penalized models use a local active-predictor design including the intercept and the L2 Hessian, holding predictor selection fixed. Singular active designs or h=1 leave unavailable diagnostics empty.
References: [Firth logistic regression](https://search.r-project.org/CRAN/refmans/logistf/html/logistf.html), [GLM influence diagnostics](https://www.statsmodels.org/stable/generated/statsmodels.stats.outliers_influence.GLMInfluence.html).

## Advanced statistics

In Statistics, missing-value imputation belongs to Data preparation; distribution/variance tests, group comparisons, effect sizes and multiple testing belong to General analysis; factorial linear, generalized/mixed regression and cross-validation belong to Regression. Advanced analysis is ordered as Bayesian inference, Resampling, Multivariate analysis, Survival analysis, then Power & sample size. Switch between current data, examples and an editable expression. Paired comparisons use complete pairs and support subject-ID matching. Wide repeated-measures inputs reject incomplete rows; impute converts them to NA. These analyses use binary64 numerics.

### Data preparation

`impute` — Prepare incomplete data by single imputation; subsequent inference omits imputation uncertainty. NA for missing cells; mean / median / mode / regression / knn with neighbours (default 5). Single imputation.
Example: impute([[1,NA],[2,4],[NA,6],[4,8]],mean)

### Distribution & variance

`kstest` — Compare continuous distributions or a sample with a fully specified distribution. Two sample lists, or kstest(data,normal,mu,sigma) / kstest(data,uniform,lower,width). Continuous null; one-sample p is asymptotic.
Example: kstest([1,2,4,5],[2,3,5,8])

`levene` — Check equality of group variances; median-centered Brown–Forsythe is less sensitive to non-normality. Separate group lists; median-centered equal-variance test.
Example: levene([1,2,4,5],[2,3,5,8])

`bartlett` — Check equal variances when group distributions are reasonably normal. Separate group lists; normality assumption.
Example: bartlett([1,2,4,5],[2,3,5,8])

### Group comparisons

`mcnemar` — Compare paired binary outcomes, such as yes/no before and after. Paired 2×2 count table; exact / corrected / asymptotic.
Example: mcnemar([[20,8],[2,15]],exact)

`twowayanova` — Compare independent observations across two factors, testing both main effects and their interaction. Independent observations, two categorical factors and a numeric response. Type III F tests with sum contrasts; interaction 1 (default) or additive 0. Normal errors and common residual variance; replication and a full-rank design are required.
Example: twowayanova([[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]],1)

`ancova` — Compare group means while adjusting for numeric covariates. Rows: numeric group ID, one or more covariates, response; confidence level (default .95); slope homogeneity check 0/1 (default 1). One factor, common slopes, Type II F tests and adjusted means at pooled covariate means.
Example: ancova([[1,1,3],[1,2,5],[1,3,4],[1,4,8],[2,2,6],[2,3,7],[2,4,9],[2,5,8],[3,1,5],[3,3,8],[3,4,10],[3,6,11]],0.95,1)

`repeatedanova` — Compare repeated conditions within the same subjects in a balanced design. Rows=subjects, columns=conditions. Second-factor levels: 1 = one-way, 2+ = two-way (first factor slowest); GG corrections.
Example: repeatedanova([[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]],1)

`friedman` — Compare three or more matched conditions by within-subject ranks; chi-square approximation with tie correction. Rows are independent subjects; columns are at least three repeated conditions. Complete matched rows, midranks and tie correction. Chi-square approximation; small samples or few conditions can give inaccurate p values. Reports Kendall W.
Example: friedman([[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]])

### Effect sizes & multiple testing

`cohend` — Describe the standardized mean difference between two independent or paired samples. Two samples; independent (pooled d) or paired (dz).
Example: cohend([1,2,4,5],[2,3,5,8],independent)

`eta2` — Describe the proportion of total variation associated with group differences. Independent groups as separate lists.
Example: eta2([1,2,4,5],[2,3,5,8])

`padjust` — Correct a family of p values when several hypotheses are tested together. p values; method bonferroni / holm / fdr (BH) / by; alpha.
Example: padjust([0.01,0.04,0.03,0.2],holm,0.05)

### Generalized regression

`linearmodel` — Fit numeric and categorical predictors with automatic interactions and Type II/III joint term F tests; supports one, two, three or more factors. General OLS with numeric and categorical predictors. Categorical positions are one-based; interaction order, Type II/III, sum/treatment coding. Uses joint partial F tests for each term. Three-way and higher interactions require enough replicated observations and identifiable columns.
Example: linearmodel([[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]],[1,2],2,3,sum)

`glm` — Model a response using a family and link suited to its distribution. Rows: predictors, response; family gaussian / binomial (0/1) / poisson / gamma / inversegaussian / nbinom; link auto or a supported link; NB2 alpha: positive fixed value (default 1) or estimate for joint ML; optional offset/exposure vector and mode. Default links: identity, logit, log, log, log, log. Model-based Wald z 95% intervals; Pearson dispersion for Gaussian/Gamma/inverse Gaussian. Use estimate as the fourth argument to estimate NB2 alpha jointly with coefficients; its uncertainty enters the observed-information covariance. Numeric alpha retains the fixed-alpha model.
Example: glm([[0,2],[1,4],[2,4],[3,7],[4,8],[5,9]],gaussian,auto,1)

`poissonreg` — Model event counts, optionally accounting for exposure. Rows: predictors, integer count response. Log link. Optional second argument row-aligned offset/exposure list; third argument offset (default) or exposure (positive, log transformed).
Example: poissonreg([[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]])

`nbreg` — Model counts with extra variation beyond a Poisson model. Rows: predictors, integer count response. NB2 with estimated dispersion. Optional offset/exposure list and offset (default) / exposure mode.
Example: nbreg([[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]])

`multinomial` — Predict unordered numeric categories from predictors. Rows: predictors, numeric category response. Smallest category is reference.
Example: multinomial([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])

`ordinal` — Predict ordered categories under a proportional-odds model. Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.
Example: ordinal([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])

### Repeated & clustered data

`mixedmodel` — Model continuous responses with repeated subjects or clusters and random effects. Rows: subject ID, predictors, response. Gaussian random intercept with up to three random slopes (0 none, a predictor position, or [1,2]); third argument reml (default) or ml; up to 5000 rows. Includes subject BLUPs, slope correlations and singular-fit diagnostics; random-slope ICC is at x=0; asymptotic Wald z inference. Optional fourth argument: profile (ML fixed-effect profile CI) or [bootstrap,200,0] (parametric fixed-effect percentile CI); alternative CI omit Wald p-values. Reports logLik/AIC/BIC; compare REML criteria only with identical fixed effects and data. Nonconverged fits withhold Wald inference.
Example: mixedmodel([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],0,reml)

`glmm` — Model clustered binary or count outcomes with subject-specific random effects. Rows: subject ID, predictors, response. Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), without quadrature refit; use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial (0/1, logit), poisson or nbinom (NB2, log). ML adaptive Gauss-Hermite quadrature: 15 points default, 1 = Laplace, otherwise 7-31. Optional fourth argument offset vector, fifth offset / exposure. Limit 1500 rows, 8 fixed coefficients. Subject-specific effects; joint marginal observed information by central differences; asymptotic Wald inference. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.
Example: glmm([[1,0,0],[1,1,0],[1,2,1],[2,0,0],[2,1,1],[2,2,1],[3,0,0],[3,1,0],[3,2,0],[4,0,1],[4,1,1],[4,2,1],[5,0,1],[5,1,0],[5,2,1],[6,0,0],[6,1,1],[6,2,0]],binomial,15)

`gee` — Estimate population-average effects for repeated or clustered outcomes. Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independence / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE. Pearson dispersion-adjusted correlation; AR(1) uses row order and equal spacing. Few-cluster Wald inference may be unreliable. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. This does not guarantee reliable inference with very few clusters.
Example: gee([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],gaussian,independence)

### Model validation

`crossvalidate` — Assess held-out predictive performance rather than training fit. Rows: predictors, response; folds, seed; split random (default) / blocked / stratified; model linear (default) / ridge / lasso / elasticnet / logistic; penalty alpha or [alpha,l1 ratio].
Example: crossvalidate([[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]],3,0)

### Bayesian inference

`bayesmean` — Estimate a normal mean with a chosen prior and predictive interval. Normal sample, unknown variance; prior mu0,kappa0,alpha0,beta0; credible level; threshold. Variance ~ InvGamma(alpha0,beta0), mean | variance ~ Normal(mu0,variance/kappa0). Defaults 0,1,2,1 are proper, scale-dependent priors. Returns Student-t mean interval and next-observation predictive interval.
Example: bayesmean([1,2,3,4,5],0,1,2,1,0.95,0)

`bayescompare` — Compare two independent normal means with posterior differences and Bayes factors. Two independent normal samples (at least 2 each); variance equal / unequal; mu0,kappa0,alpha0,beta0; credible level; IID posterior draws (2000-100000), seed. H1: independent Normal(mu0,variance/kappa0) means with shared (equal) or independent (unequal) InvGamma(alpha0,beta0) variances. H0: B-A=0 with nuisance prior conditioned from H1. BF10/BF01 use Savage-Dickey, not a JZS/Cauchy prior. Reports B-A mean, equal-tailed credible interval, P(muB>muA), and posterior effect (B-A)/sqrt((varianceA+varianceB)/2). Equal-mode difference summaries and BF are analytic; unequal BF uses numerical t convolution, unequal intervals/probability and effect intervals use simulation. MCSE, draws and seed are reported. Defaults are proper but unit-dependent; choose priors before inspecting outcomes.
Example: bayescompare([10,11,9,10,12],[13,14,12,15,13],equal,0,0.01,2,1,0.95,20000,0)

`bayesproportion` — Estimate a binary success proportion using a Beta prior. Binary 0/1 list or [[successes,trials],...]; Beta prior alpha, beta (default 1,1); credible level; threshold p0 in (0,1). Returns equal-tailed interval, P(p>p0), next-success probability and BF10 (Beta alternative / point null p=p0).
Example: bayesproportion([1,1,0,1,0,1,1,1,0,1],1,1,0.95,0.5)

`bayesrate` — Estimate a Poisson event rate using counts and exposure. Count list (one exposure unit each) or [[count,exposure],...]; Gamma prior shape, rate (inverse scale, default 1,1); credible level; nonnegative threshold. Equal-tailed rate interval and predictive count mean/SD for one exposure unit.
Example: bayesrate([0,2,1,3,2],1,1,0.95,1)

### Resampling

`bootstrapci` — Estimate an IID statistic interval by resampling observed values. Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.
Example: bootstrapci([1,2,3,4,5,8],mean,0.95,2000,0)

`bayesbootstrap` — Quantify posterior uncertainty in statistics using random weights on observations. Dirichlet(1,…,1) weights on IID observed values; mean / median / variance / stdev, credible level, draws, seed. Median estimate uses the ordinary sample median (average the two middle values for even n); posterior draws use the Lower weighted quantile (smallest value with weighted CDF >= 0.5); variance/SD use population weights. Equal-tailed simulated posterior interval and histogram. Two samples: bayesbootstrap(A,B,mean,0.95,10000,0,independent); paired uses shared row weights. Comparison is statistic(B) - statistic(A).
Example: bayesbootstrap([1,2,3,4,5,8],mean,0.95,10000,0)

### Multivariate analysis

`pca` — Summarize correlated numeric features using fewer components. Rows=observations, columns=features; components, standardize 1/0.
Example: pca([[1,2],[2,1],[3,4],[4,3],[5,7]],2,1)

`kmeans` — Group observations by numeric-feature similarity; check feature scales first. Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.
Example: kmeans([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)

### Survival analysis

`survivalanalysis` — Analyze censored time-to-event data as a Kaplan–Meier, log-rank and optional Cox set. Rows: time, event (0/1), group ID, optional Cox predictors; Cox 0=off, 1=on; then ties and the PH check.
Example: survivalanalysis([[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]],0,efron,-1,1)

`kaplanmeier` — Estimate survival over time while accounting for censoring. Rows: time, event (1=event, 0=censored); confidence level.
Example: kaplanmeier([[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]],0.95)

`logrank` — Compare survival between two groups without adjusting for predictors. Two time/event tables. Current data: time, event, group (exactly two groups).
Example: logrank([[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]])

`cox` — Relate predictors to event hazard; review proportional hazards. Rows: time, event 0/1, predictors. Ties efron (default) or breslow; entry column for left truncation (-1 none); PH check 0/1.
Example: cox([[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]],efron,-1,1)

### Power & sample size

`testpower` — Evaluate power for a planned t-test design and effect size. Cohen d, n per group/pairs, alpha, independent / paired / onesample, alternative two (default) / greater / less. Exact noncentral-t power.
Example: testpower(0.5,64,0.05,independent)

`samplesize` — Plan the sample size needed for a target t-test power. Cohen d, target power, alpha, design, alternative. Exact noncentral-t power.
Example: samplesize(0.5,0.8,0.05,independent)

Bayesian analyses assume independent observations and the stated likelihood with proper conjugate priors; intervals are equal-tailed posterior credible intervals. A Bayes factor is not a posterior hypothesis probability and depends on the prior. References: [Stanford conjugate priors](https://web.stanford.edu/class/stats200/Lecture21.pdf), [normal-inverse-gamma analysis](https://treese41528.github.io/ComputationalDataScience/Website/part3_bayesian/chapter5/ch5_2-prior-distributions.html).

The Bayesian bootstrap uses Dirichlet(1,…,1) weights on observed values and equal-tailed posterior intervals, rather than a conjugate parametric prior. Reference: [Rubin, The Bayesian Bootstrap (1981)](https://people.eecs.berkeley.edu/~jordan/sail/readings/rubin.pdf).

Models return errors on failed convergence or non-identifiability; a finite nonconverged random-slope fit retains estimates and diagnostics but withholds Wald SE, p-values and CI. Cox supports Breslow/Efron ties, optional left truncation and a Grambsch–Therneau scaled-Schoenfeld proportional-hazards check; ordinal logistic assumes proportional odds. Mixed models support a random intercept plus up to three random slopes under ML or REML; GEE supports independent, exchangeable and AR(1) working correlations. Repeated-measures ANOVA covers balanced one- and two-way within-subject designs with GG corrections. Single imputation (mean, median, mode, regression or k-NN) does not propagate imputation uncertainty. Cross-validation covers linear, ridge, lasso, elastic-net and logistic fits with random, blocked or stratified splits. Firth inference uses profile penalized-likelihood intervals; bootstrap CIs use the percentile method, not BCa.

ANCOVA: [partial ANOVA tests](https://www.statsmodels.org/stable/generated/statsmodels.stats.anova.anova_lm.html), [equal slopes](https://itl.nist.gov/div898/software/dataplot/refman1/auxillar/equalslo.htm). GLM: [families, links and dispersion](https://www.statsmodels.org/stable/glm.html).

Mixed-model profile/bootstrap: [lme4 confidence intervals](https://lme4.github.io/lme4/reference/confint.merMod.html). GEE covariance: [statsmodels covariance options](https://www.statsmodels.org/stable/generated/statsmodels.genmod.generalized_estimating_equations.GEE.fit.html).

GLMM: [lme4 adaptive quadrature reference](https://lme4.github.io/lme4/reference/glmer.html).

Bayesian Two-Sample Comparison: [Savage-Dickey density ratio and compatible null priors](https://statproofbook.github.io/P/bf-sddr.html).

Factorial linear models: [Type II/III ANOVA and sum contrasts](https://www.statsmodels.org/stable/examples/notebooks/generated/interactions_anova.html). Welch / Games–Howell: [SciPy unequal-variance ANOVA](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.f_oneway.html), [studentized range](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.studentized_range.html). Friedman: [tie-corrected repeated rank test](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.friedmanchisquare.html).

Post-hoc suites: Kruskal–Wallis includes pairwise Mann–Whitney + Holm (separately ranked pairs, not Dunn); Friedman includes paired Wilcoxon + Holm; repeated-measures ANOVA includes paired t + Holm and normality/Q–Q checks on within-subject contrasts. Global and pairwise tests answer different questions.

Test selection and diagnostics: [NIST t tests](https://www.itl.nist.gov/div898/handbook/eda/section3/eda353.htm), [Levene/Brown–Forsythe](https://www.itl.nist.gov/div898/handbook/eda/section3/eda35a.htm), [normal probability plots](https://www.itl.nist.gov/div898/handbook/eda/section3/normprpl.htm), [Wilcoxon assumptions](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.wilcoxon.html).
