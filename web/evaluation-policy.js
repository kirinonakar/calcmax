// Keep the typing-preview policy aligned with math/EvaluationPolicy.kt.
const multiArgumentFunctions=new Set([
  'bayesproportion','bayesmean','bayesrate','bayescompare',
  'round','roundh','nCr','nPr','gcd','lcm','quotient','remainder','mod','divmod',
  'collect','subs','diff','integrate','limit','series','sum','product','solve',
  'nsolve','nintegrate','nderivative','minimum','maximum','piecewise',
  'polar','pol','rec','rnd','randInt','eng','dms',
  'linsolve','dot','cross','angle','projection','regression','qty','convert',
  'tpdf','tcdf','invt','chi2pdf','chi2cdf','fpdf','fcdf',
  'binompdf','binomcdf','poissonpdf','poissoncdf','geometpdf','geometcdf',
  'ttest','ztest','propztest','propztest2','chi2test','anova','tukey','wilcoxon','mannwhitney','kruskal','tinterval','zinterval',
  'ancova','glm','padjust','cohend','eta2','levene','bartlett','mcnemar','survivalanalysis','kaplanmeier','logrank','cox','repeatedanova','mixedmodel','glmm','gee','multinomial','ordinal','poissonreg','nbreg','bootstrapci','bayesbootstrap','testpower','samplesize','kstest','crossvalidate','pca','kmeans','impute',
  'tvmfv','tvmpv','tvmpmt','tvmn','tvmrate','npv','irr','amort','cagr'
]);
const previewFunctions=new Set(['log','nthroot','mixed','mod','divmod']);

export function requiresExplicitEvaluation(tree,userFunctions=new Set()) {
  return tree.kind==='call'&&!previewFunctions.has(tree.value)&&
    ((tree.args?.length||0)>1||multiArgumentFunctions.has(tree.value)||userFunctions.has(tree.value))||
    (tree.args||[]).some(child=>requiresExplicitEvaluation(child,userFunctions));
}
