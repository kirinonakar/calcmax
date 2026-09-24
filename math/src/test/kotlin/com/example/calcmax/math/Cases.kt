package com.example.calcmax.math
import java.io.File

fun main(args: Array<String>) {
    val cases=listOf(
        "2+3*4" to "14", "1/3+1/6" to "1/2", "sqrt(8)" to "2*sqrt(2)", "sqrt(12)" to "2*sqrt(3)",
        "sin(pi/6)" to "1/2", "cos(pi)" to "-1", "sin(30°)" to "1/2", "-2^2" to "-4", "2^3^2" to "512",
        "2°20′30″" to "281/120", "2°20′30″+0°39′30″" to "3",
        "0.1+0.2" to "3/10", "{1,2}" to "{1, 2}", "123456789012345678901234567890+1" to "123456789012345678901234567891",
        "diff(x^3,x)" to "3*x**2", "diff(sin(x^2),x)" to "2*x*cos(x**2)", "diff(exp(x),x,3)" to "exp(x)",
        "integrate(x^2,x)" to "C + x**3/3", "integrate(x^2*sin(x),x)" to "C - x**2*cos(x) + 2*x*sin(x) + 2*cos(x)",
        "sinc(0)" to "1", "sinc(pi)" to "0", "sinc(pi/2)" to "2/pi",
        "integrate(x^2,x,0,1)" to "1/3", "limit(sin(x)/x,x,0)" to "1", "limit(1/x,x,oo)" to "0",
        "limit(1/x,x,0,left)" to "-oo", "limit(1/x,x,0,right)" to "oo",
        "factor(x^4-1)" to "(x - 1)*(x + 1)*(x**2 + 1)", "expand((x+1)^2)" to "x**2 + 2*x + 1",
        "simplify((x^2-1)/(x-1))" to "x + 1", "subs(x^2,x,3)" to "9", "solve(x^2-5x+6=0,x)" to "{2, 3}",
        "solve(x^2+1=0,x)" to "{-I, I}", "solve(x^3-1=0,x)" to "{1, -1/2 - sqrt(3)*I/2, -1/2 + sqrt(3)*I/2}",
        "solve(x^2<4,x)" to "(-2 < x) & (x < 2)", "det([[1,2],[3,4]])" to "-2", "trace([[1,2],[3,4]])" to "5",
        "rank([[1,2],[2,4]])" to "1", "dot([1,2,3],[4,5,6])" to "32", "norm([3,4])" to "5",
        "mean([1,2,3,4])" to "5/2", "median([1,2,3,4])" to "5/2", "variance([1,2,3])" to "2/3",
        "regression([[1,2],[2,4],[3,6]],linear)" to "2*x", "convert(1,m,cm)" to "100", "convert(32,degF,degC)" to "0",
        "convert(1,KiB,byte)" to "1024", "gcd(48,18)" to "6", "lcm(6,8)" to "24", "nCr(10,3)" to "120",
        "nPr(5,2)" to "20", "5!" to "120", "abs(3+4i)" to "5", "(1+i)^2" to "2*I", "conj(3+4i)" to "3 - 4*I",
        "sum(x^2,x,1,10)" to "385", "product(x,x,1,5)" to "120", "cbrt(-8)" to "-2", "log(8,2)" to "3",
        "piecewise([x,x>0],[-x,true])" to "Piecewise((x, x > 0), (-x, True))", "prime(1000)" to "7919", "isprime(123457)" to "True", "factorint(360)" to "Matrix([\n[2, 3],\n[3, 2],\n[5, 1]])",
        "qty(2,m)+qty(30,cm)" to "23/10 m", "convert(qty(1,kg)*qty(2,mps2),N)" to "2",
        "qty(1,km)/qty(1,m)" to "1000", "convert(qty(32,degF),degC)" to "0"
    )
    val extras=listOf("1/0","0^0","inverse([[1,2],[2,4]])","dot([1,2],[1,2,3])","convert(1,m,kg)","convert(-1,K,degC)","factorial(-1)","sqrt(x^2)","solve((x^2-1)/(x-1)=2,x)","sin(x)","cos(2*x)","[cos(t),sin(t)]","1/x","tan(x)","nsolve(cos(x)-x,x,0,1)","nintegrate(sin(x),x,0,pi)","stats([1,2,3])","series(exp(x),x,0,4)","solve([x+y=3,x-y=1],[x,y])","integrate(exp(-x^2),x)","integrate(exp(-x^2)*cos(2x),(x,0,oo))","integrate(exp(-x^2)*cos(2*x),x,0,oo)","minimum(x^2,x,-2,3)","maximum(x^2,x,-2,3)","eigenvalues([[1,0],[0,2]])","lu([[1,2],[3,4]])","f(3)","x+1")
    val values=cases.map { (source,expected)->"{\"source\":${quote(source)},\"expected\":${quote(expected)},\"tree\":${Parser(source).parse().json()}}" }+extras.map { "{\"source\":${quote(it)},\"tree\":${Parser(it).parse().json()}}" }
    File(args[0]).apply { parentFile.mkdirs();writeText(values.joinToString(",","[","]")) }
}
