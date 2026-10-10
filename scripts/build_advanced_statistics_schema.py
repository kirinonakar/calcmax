"""Author shared advanced-analysis presets and bilingual catalog help."""
import json
import ast
from pathlib import Path
from statistics_help import USES, enrich_help
from statistics_social_schema import SPECS as SOCIAL_SPECS, forms as social_forms

ROOT = Path(__file__).resolve().parents[1]
GROUPS = '[1,2,4,5],[2,3,5,8]'
COUNTS = '[[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]]'
CATEGORIES = '[[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]]'
CLUSTERS = '[[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]]'
GLMM = '[[1,0,0],[1,1,0],[1,2,1],[2,0,0],[2,1,1],[2,2,1],[3,0,0],[3,1,0],[3,2,0],[4,0,1],[4,1,1],[4,2,1],[5,0,1],[5,1,0],[5,2,1],[6,0,0],[6,1,1],[6,2,0]]'
SURVIVAL = '[[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]]'
specs = [
    ('tinterval','t confidence interval','t 신뢰구간','list',',95','[1,2,3,4,5]','Mean confidence interval with unknown population SD: tinterval(level,data) or tinterval(level,mean,SD,n).','모집단 표준편차를 모르는 평균의 신뢰구간: tinterval(수준,자료) 또는 tinterval(수준,평균,표준편차,n).'),
    ('zinterval','z confidence interval','z 신뢰구간','list',',95,2','[1,2,3,4,5]','Mean confidence interval with known population SD: zinterval(level,sigma,data) or zinterval(level,sigma,mean,n).','모집단 표준편차를 아는 평균의 신뢰구간: zinterval(수준,모집단표준편차,자료) 또는 zinterval(수준,모집단표준편차,평균,n).'),
    ('propztest','One-sample proportion z test','단일 표본 비율 z 검정','table','','[[60,100]]',
     'propztest(p0,data) or propztest(p0,successes,trials); binary 0/1 data or [[successes,trials],...]. Optional both / left / right. Null-based standard error, no continuity correction; independent observations and adequate expected counts are required.',
     'propztest(p0,자료) 또는 propztest(p0,성공수,시행수); 0/1 자료 또는 [[성공수,시행수],...]. 선택적 both·left·right. 귀무가설의 표준오차, 연속성 보정 없음. 독립 관측·충분한 기대빈도가 필요합니다.'),
    ('propztest2','Two-sample proportion z test','두 표본 비율 z 검정','table','','[[60,100],[45,100]]',
     'propztest2(A,B) or propztest2(successesA,trialsA,successesB,trialsB). H0: pA=pB; pooled standard error, no continuity correction. Optional both / left / right for A−B. Independent binary samples; paired outcomes require McNemar.',
     'propztest2(A,B) 또는 propztest2(성공수A,시행수A,성공수B,시행수B). H0: pA=pB, 합동 표준오차, 연속성 보정 없음. A−B에 대한 both·left·right 선택. 독립 이항 표본용이며 대응 결과는 McNemar를 사용합니다.'),
    ('shapiro','Shapiro–Wilk','Shapiro–Wilk','list','','[1,2,3,4,5]','Normality test for 3 to 5000 observations; interpret with Q–Q plots.','관측값 3~5000개의 정규성 검정; Q–Q plot과 함께 해석합니다.'),
    ('tukey','Tukey–Kramer','Tukey–Kramer','groups','',GROUPS,'All pairwise mean comparisons for independent groups with equal variances.','등분산 독립 그룹의 모든 쌍별 평균 사후비교.'),
    ('gameshowell','Games–Howell','Games–Howell','groups','',GROUPS,'All pairwise mean comparisons for independent groups with unequal variances.','이분산 독립 그룹의 모든 쌍별 평균 사후비교.'),
    ('linearmodel','Factorial linear model / ANOVA','요인 선형회귀 / ANOVA','table',',[1,2],2,3,sum','[[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]]','General OLS with numeric and categorical predictors. Categorical positions are one-based; interaction order, Type II/III, sum/treatment coding. Uses joint partial F tests for each term. Three-way and higher interactions require enough replicated observations and identifiable columns.','숫자·범주 설명변수를 포함한 일반 OLS. 범주 변수 번호는 1부터 시작하며 상호작용 차수·Type II/III·sum/treatment 코딩을 지정합니다. 항마다 여러 계수를 부분 F로 함께 검정합니다. 3요인 이상 상호작용에도 충분한 반복 관측·식별 가능한 열이 필요합니다.'),
    ('twowayanova','Two-way ANOVA','이요인 ANOVA','table',',1','[[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]]','Independent observations, two categorical factors and a numeric response. Type III F tests with sum contrasts; interaction 1 (default) or additive 0. Normal errors and common residual variance; replication and a full-rank design are required.','독립 관측·두 범주 요인·숫자 반응. 합이 0인 대비의 Type III F 검정. 상호작용 1(기본) 또는 가법 0. 정규 오차·공통 잔차분산을 가정하며 반복 관측과 식별 가능한 설계가 필요합니다.'),
    ('friedman','Friedman test','Friedman 검정','table','','[[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]]','Rows are independent subjects; columns are at least three repeated conditions. Complete matched rows, midranks and tie correction. Chi-square approximation; small samples or few conditions can give inaccurate p values. Reports Kendall W.','행은 독립 대상, 열은 3개 이상 반복 조건입니다. 완전한 대응 행·평균순위·동점 보정. χ² 근사이므로 소표본·소수 조건에서 p값이 부정확할 수 있습니다. Kendall W를 제공합니다.'),
    ('ancova','ANCOVA','ANCOVA (공분산분석)','table',',0.95,1','[[1,1,3],[1,2,5],[1,3,4],[1,4,8],[2,2,6],[2,3,7],[2,4,9],[2,5,8],[3,1,5],[3,3,8],[3,4,10],[3,6,11]]','Rows: numeric group ID, one or more covariates, response; confidence level (default .95); slope homogeneity check 0/1 (default 1). One factor, common slopes, Type II F tests and adjusted means at pooled covariate means.','열: 숫자 그룹 ID, 하나 이상의 공변량, 종속변수; 신뢰수준(기본 .95), 기울기 동질성 검정 0/1(기본 1). 일요인·공통 기울기, Type II F 검정, 전체 공변량 평균에서의 조정 평균.'),
    ('glm','Generalized linear model (GLM)','GLM (일반화 선형모형)','table',',gaussian,auto,1','[[0,2],[1,4],[2,4],[3,7],[4,8],[5,9]]','Rows: predictors, response; family gaussian / binomial (0/1) / poisson / gamma / inversegaussian / nbinom; link auto or a supported link; NB2 alpha: positive fixed value (default 1) or estimate for joint ML; optional offset/exposure vector and mode. Default links: identity, logit, log, log, log, log. Model-based Wald z 95% intervals; Pearson dispersion for Gaussian/Gamma/inverse Gaussian. Use estimate as the fourth argument to estimate NB2 alpha jointly with coefficients; its uncertainty enters the observed-information covariance. Numeric alpha retains the fixed-alpha model.','열: 설명변수, 반응변수; 분포 gaussian·binomial(0/1)·poisson·gamma·inversegaussian·nbinom; 연결함수 auto 또는 지원 함수; NB2 alpha: 양수 고정값(기본 1) 또는 estimate(공동 ML 추정); 선택적 오프셋·노출량 목록과 유형. 기본 연결함수는 identity·logit·log·log·log·log. 모형 기반 Wald z 95% 구간; 정규·Gamma·역가우스는 Pearson 분산 추정. 넷째 인수를 estimate로 지정하면 NB2 alpha와 계수를 공동 ML 추정하며 관측 정보행렬에 alpha의 불확실성을 반영합니다. 숫자를 입력하면 alpha를 고정합니다.'),
    ('padjust','Multiple testing','다중검정 보정','list',',holm,0.05','[0.01,0.04,0.03,0.2]', 'p values; method bonferroni / holm / fdr (BH) / by; alpha.', 'p값 목록; 방법 bonferroni / holm / fdr (BH) / by; 유의수준.'),
    ('cohend',"Cohen’s d","Cohen의 d",'groups',',independent',GROUPS,'Two samples; independent (pooled d) or paired (dz).','두 표본; independent(합동 SD) 또는 paired(차이의 SD).'),
    ('eta2','η² effect size','η² 효과크기','groups','',GROUPS,'Independent groups as separate lists.','독립 그룹별 목록.'),
    ('levene','Levene / Brown–Forsythe','Levene / Brown–Forsythe','groups','',GROUPS,'Separate group lists; median-centered equal-variance test.','그룹별 목록; 중앙값 기준 등분산 검정.'),
    ('bartlett','Bartlett','Bartlett','groups','',GROUPS,'Separate group lists; normality assumption.','그룹별 목록; 정규성 가정.'),
    ('mcnemar','McNemar','McNemar','table',',exact','[[20,8],[2,15]]','Paired 2×2 count table; exact / corrected / asymptotic.','대응 2×2 빈도표; exact / corrected / asymptotic.'),
    ('bayesproportion','Bayesian proportion','베이지안 비율','list',',1,1,0.95,0.5','[1,1,0,1,0,1,1,1,0,1]','Binary 0/1 list or [[successes,trials],...]; Beta prior alpha, beta (default 1,1); credible level; threshold p0 in (0,1). Returns equal-tailed interval, P(p>p0), next-success probability and BF10 (Beta alternative / point null p=p0).','0/1 목록 또는 [[성공 수,시행 수],...]; Beta 사전 alpha,beta(기본 1,1), 구간 수준, 기준 p0(0~1 사이). 등꼬리 구간·P(p>p0)·다음 성공 확률·BF10(Beta 대립 / p=p0 점귀무).'),
    ('bayesmean','Bayesian mean','베이지안 평균','list',',0,1,2,1,0.95,0','[1,2,3,4,5]','Normal sample, unknown variance; prior mu0,kappa0,alpha0,beta0; credible level; threshold. Variance ~ InvGamma(alpha0,beta0), mean | variance ~ Normal(mu0,variance/kappa0). Defaults 0,1,2,1 are proper, scale-dependent priors. Returns Student-t mean interval and next-observation predictive interval.','분산 미지의 정규 표본; 사전 mu0,kappa0,alpha0,beta0, 구간 수준, 기준값. 분산 ~ InvGamma(alpha0,beta0), 평균|분산 ~ Normal(mu0,분산/kappa0). 기본 0,1,2,1은 자료 척도에 맞춰 조절할 적정 사전분포. 평균의 t 구간과 다음 관측 예측구간.'),
    ('bayescompare','Bayesian Two-Sample Comparison','베이지안 두 표본 비교','groups',',equal,0,0.01,2,1,0.95,20000,0','[10,11,9,10,12],[13,14,12,15,13]',
     'Two independent normal samples (at least 2 each); variance equal / unequal; mu0,kappa0,alpha0,beta0; credible level; IID posterior draws (2000-100000), seed. H1: independent Normal(mu0,variance/kappa0) means with shared (equal) or independent (unequal) InvGamma(alpha0,beta0) variances. H0: B-A=0 with nuisance prior conditioned from H1. BF10/BF01 use the Savage-Dickey density ratio. Reports B-A mean, equal-tailed credible interval, P(muB>muA), and posterior effect (B-A)/sqrt((varianceA+varianceB)/2). Equal-mode difference summaries and BF are analytic; unequal BF uses numerical t convolution, unequal intervals/probability and effect intervals use simulation. MCSE, draws and seed are reported. Defaults are proper but unit-dependent; choose priors before inspecting outcomes.',
     '독립 정규 표본 두 개(각 2개 이상); 분산 equal·unequal; mu0,kappa0,alpha0,beta0; 구간 수준; IID 사후 추출 수(2000~100000), 시드. H1: 평균|분산은 독립 Normal(mu0,분산/kappa0), 분산은 공통(등분산) 또는 독립(이분산) InvGamma(alpha0,beta0). H0: B-A=0이며 H1을 이 조건으로 제한한 방해모수 사전분포를 사용합니다. BF10/BF01은 Savage-Dickey 밀도비를 사용합니다. B-A 평균·등꼬리 신용구간·P(muB>muA)·사후 효과크기 (B-A)/sqrt((분산A+분산B)/2)를 출력합니다. 등분산 차이 요약·BF는 해석적, 이분산 BF는 t 합성곱 수치 적분, 이분산 구간·확률 및 효과크기 구간은 시뮬레이션입니다. MCSE·추출 수·시드 포함. 기본 사전분포는 적정하지만 단위에 의존하므로 결과를 보기 전에 척도에 맞게 지정하세요.'),
    ('bayesrate','Bayesian Poisson rate','베이지안 발생률','list',',1,1,0.95,1','[0,2,1,3,2]','Count list (one exposure unit each) or [[count,exposure],...]; Gamma prior shape, rate (inverse scale, default 1,1); credible level; nonnegative threshold. Equal-tailed rate interval and predictive count mean/SD for one exposure unit.','횟수 목록(관측당 노출 1) 또는 [[횟수,노출량],...]; Gamma 사전 shape,rate(척도의 역수, 기본 1,1), 구간 수준, 0 이상 기준값. 발생률 등꼬리 구간과 노출 1단위의 예측 횟수 평균·SD.'),
    ('kaplanmeier','Kaplan–Meier','Kaplan–Meier','table',',0.95',SURVIVAL,'Rows: time, event (1=event, 0=censored); confidence level.','열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.'),
    ('logrank','Log-rank','로그순위 검정','survivalgroups','','[[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]]','Two time/event tables. Current data: time, event, group (exactly two groups).','두 시간/사건 표. 현재 데이터 열: 시간, 사건, 그룹(2개).'),
    ('survivalanalysis','Survival analysis','생존분석','table',',0,efron,-1,1','[[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]]','Rows: time, event (0/1), group ID, optional Cox predictors; Cox 0=off, 1=on; then ties and the PH check.','열: 시간, 사건(0/1), 그룹 ID, 선택적 Cox 설명변수. Cox 0=끔, 1=켬; 이어서 동률 처리와 PH 검정.'),
    ('cox','Cox regression','Cox 회귀','table',',efron,-1,1','[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]]','Rows: time, event 0/1, predictors. Ties efron (default) or breslow; entry column for left truncation (-1 none); PH check 0/1.','열: 시간, 사건 0/1, 설명변수. 동률 efron(기본)/breslow, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.'),
    ('repeatedanova','Repeated-measures ANOVA','반복측정 ANOVA','table',',1','[[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]]','Rows=subjects, columns=conditions. Second-factor levels: 1 = one-way, 2+ = two-way (first factor slowest); GG corrections.','행=대상, 열=조건. 둘째 요인 수준: 1=일요인, 2 이상=이요인(첫 요인 최외곽); GG 보정.'),
    ('mixedmodel','Mixed model','혼합모형','table',',0,reml',CLUSTERS,'Rows: subject ID, predictors, response. Gaussian random intercept with up to three random slopes (0 none, a predictor position, or [1,2]); third argument reml (default) or ml; up to 5000 rows. Includes subject BLUPs, slope correlations and singular-fit diagnostics; random-slope ICC is at x=0; asymptotic Wald z inference.','열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기(0 없음, 변수 위치, 또는 [1,2]); 셋째 인수 reml(기본)·ml; 최대 5000행. 대상별 BLUP·기울기 상관·singular 진단 포함; 기울기 ICC는 x=0 기준; 점근 Wald z 추론.'),
    ('glmm','Generalized mixed model (GLMM)','일반화 혼합모형 (GLMM)','table',',binomial,15',GLMM,'Rows: subject ID, predictors, response. Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial (0/1, logit), poisson or nbinom (NB2, log). ML adaptive Gauss-Hermite quadrature: 15 points default, 1 = Laplace, otherwise 7-31. Optional fourth argument offset vector, fifth offset / exposure. Limit 1500 rows, 8 fixed coefficients. Subject-specific effects; joint marginal observed information by central differences; asymptotic Wald inference.','열: 대상 ID, 설명변수, 반응. 랜덤 절편 + 선택적 상관 랜덤 기울기 1개(일곱째 인수: 선택한 설명변수 번호, 0 없음). 기울기는 2차원 Laplace(셋째 인수 1), 번호 앞에 [],offset,likelihood를 지정합니다. 공분산·조건부 최빈값은 원래 단위로 표시합니다. 랜덤 절편: binomial(0/1, 로짓), poisson·nbinom(NB2, 로그). ML 적응형 Gauss-Hermite 적분: 기본 15점, 1=Laplace, 그 외 7~31점. 선택적 넷째 인수 오프셋 목록, 다섯째 offset·exposure. 최대 1500행·고정계수 8개. 대상별 조건부 효과, 중앙차분 관측 정보행렬·점근 Wald 추론.'),
    ('gee','GEE','GEE','table',',gaussian,independence',CLUSTERS,'Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independence / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE. Pearson dispersion-adjusted correlation; AR(1) uses row order and equal spacing. Few-cluster Wald inference may be unreliable.','열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE. Pearson 분산 보정 상관; AR(1)은 행 순서·등간격 사용. 소수 군집의 Wald 추론은 부정확할 수 있습니다.'),
    ('multinomial','Multinomial logistic','다항 로지스틱','table','',CATEGORIES,'Rows: predictors, numeric category response. Smallest category is reference.','열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준.'),
    ('ordinal','Ordinal logistic','순서형 로지스틱','table','',CATEGORIES,'Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.','열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓.'),
    ('poissonreg','Poisson regression','포아송 회귀','table','',COUNTS,'Rows: predictors, integer count response. Log link. Optional second argument row-aligned offset/exposure list; third argument offset (default) or exposure (positive, log transformed).','열: 설명변수, 정수 빈도 반응. 로그 연결함수. 선택적 둘째 인수 행별 오프셋·노출량 목록; 셋째 인수 offset(기본)·exposure(양수, 로그 변환).'),
    ('nbreg','Negative binomial regression','음이항 회귀','table','','[[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]]','Rows: predictors, integer count response. NB2 with estimated dispersion. Optional offset/exposure list and offset (default) / exposure mode.','열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정. 선택적 오프셋·노출량 목록과 offset(기본)·exposure 모드.'),
    ('bootstrapci','Bootstrap confidence interval','부트스트랩 신뢰구간','list',',mean,0.95,2000,0','[1,2,3,4,5,8]','Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.','통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식.'),
    ('bayesbootstrap','Bayesian Bootstrap','베이지안 부트스트랩','list',',mean,0.95,10000,0','[1,2,3,4,5,8]','Dirichlet(1,…,1) weights on IID observed values; mean / median / variance / stdev, credible level, draws, seed. Median estimate uses the ordinary sample median (average the two middle values for even n); posterior draws use the Lower weighted quantile (smallest value with weighted CDF >= 0.5); variance/SD use population weights. Equal-tailed simulated posterior interval and histogram. Two samples: bayesbootstrap(A,B,mean,0.95,10000,0,independent); paired uses shared row weights. Comparison is statistic(B) - statistic(A).','독립 관측값의 Dirichlet(1,…,1) 가중치; mean / median / variance / stdev, 베이지안 구간 수준·추출 수·시드. 중앙값 estimate는 일반 표본 중앙값(짝수 표본은 가운데 두 값의 평균)이고 사후추출은 Lower weighted quantile(가중 누적확률이 0.5 이상인 최소값), 분산·SD는 모집단 가중치 기준. 등꼬리 사후 구간·히스토그램. 두 표본: bayesbootstrap(A,B,mean,0.95,10000,0,independent); paired는 같은 행의 가중치를 공유합니다. 차이는 통계량(B) - 통계량(A)입니다.'),
    ('testpower','Power','검정력','none','', '0.5,64,0.05,independent','Cohen d, n per group/pairs, alpha, independent / paired / onesample, alternative two (default) / greater / less. Exact noncentral-t power.','Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample, 대립가설 two(기본) / greater / less. 정확 noncentral-t 검정력.'),
    ('samplesize','Sample size','표본수','none','','0.5,0.8,0.05,independent','Cohen d, target power, alpha, design, alternative. Exact noncentral-t power.','Cohen d, 목표 검정력, 유의수준, 설계, 대립가설. 정확 noncentral-t 검정력.'),
    ('kstest','Kolmogorov–Smirnov','Kolmogorov–Smirnov','groups','',GROUPS,'Two sample lists, or kstest(data,normal,mu,sigma) / kstest(data,uniform,lower,width). Continuous null; one-sample p is asymptotic.','두 표본 목록 또는 kstest(data,normal,평균,SD) / kstest(data,uniform,하한,폭). 연속분포 가정; 일표본 p는 근사.'),
    ('crossvalidate','Cross-validation','교차검증','table',',3,0','[[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]]','Rows: predictors, response; folds, seed; split random (default) / blocked / stratified; model linear (default) / ridge / lasso / elasticnet / logistic; penalty alpha or [alpha,l1 ratio].','열: 설명변수, 반응; 폴드 수, 시드; 분할 random(기본) / blocked / stratified; 모형 linear(기본) / ridge / lasso / elasticnet / logistic; 벌점 alpha 또는 [alpha,l1 비율].'),
    ('pca','PCA','주성분 분석','table',',2,1','[[1,2],[2,1],[3,4],[4,3],[5,7]]','Rows=observations, columns=features; components, standardize 1/0.','행=관측, 열=변수; 주성분 수, 표준화 1/0.'),
    ('kmeans','K-means clustering','K-means 군집','table',',2,0','[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]]','Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.','숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도.'),
    ('impute','Missing-value imputation','결측치 대체','table',',mean','[[1,NA],[2,4],[NA,6],[4,8]]','NA for missing cells; mean / median / mode / regression / knn with neighbours (default 5). Single imputation.','결측값은 NA; mean / median / mode / regression / knn(이웃 수 기본 5). 단일 대체.'),
]
specs.extend(SOCIAL_SPECS)
schema = [{'id':id_,'label':label,'ko':ko,'input':layout,'suffix':suffix,'example':f'{id_}({data}{suffix})','help':help_,'helpKo':helpko} for id_,label,ko,layout,suffix,data,help_,helpko in specs]
# Menu placement and order are shared by Android and Web; function IDs stay stable.
menus = [
    ('preparation', 'Data preparation', '데이터 준비', ['impute']),
    ('general', 'Categorical data', '범주형 자료', ['propztest','propztest2','mcnemar','cramerv','phi','cohenkappa']),
    ('general', 'Reliability', '신뢰도', ['cronbach']),
    ('tests', 'Distribution & variance', '분포·분산 검정', ['shapiro','kstest','levene','bartlett']),
    ('tests', 'Post-hoc comparisons', '사후비교', ['tukey','gameshowell','dunn']),
    ('tests', 'Group comparisons', '그룹 비교', ['twowayanova','ancova','manova','repeatedanova','friedman']),
    ('tests', 'Effect sizes & multiple testing', '효과크기·다중검정', ['cohend','eta2','padjust']),
    ('tests', 'Confidence intervals', '신뢰구간', ['tinterval','zinterval']),
    ('models', 'Generalized regression', '일반화 회귀', ['linearmodel','glm','poissonreg','nbreg','zeroinflated','tobit','quantreg','multinomial','ordinal']),
    ('models', 'Mediation & moderation', '매개·조절 분석', ['mediation','moderation']),
    ('models', 'Repeated & clustered data', '반복·군집 자료', ['mixedmodel','glmm','gee']),
    ('models', 'Model validation', '모형 검증', ['crossvalidate']),
    ('advanced', 'Bayesian inference', '베이지안 추론', ['bayesmean','bayescompare','bayesproportion','bayesrate']),
    ('advanced', 'Resampling', '재표집', ['bootstrapci','bayesbootstrap']),
    ('advanced', 'Measurement & structural models', '측정·구조 모형', ['efa','cfa','sem']),
    ('advanced', 'Multivariate analysis', '다변량 분석', ['pca','discriminantanalysis','kmeans','hcluster']),
    ('advanced', 'Survival analysis', '생존분석', ['survivalanalysis','kaplanmeier','logrank','cox']),
    ('advanced', 'Power & sample size', '검정력·표본수', ['testpower','samplesize']),
]
definitions = {item['id']: item for item in schema}
schema = []
for section, group, group_ko, ids in menus:
    for id_ in ids:
        schema.append(dict(definitions[id_], section=section, group=group, groupKo=group_ko))
assert len(schema) == len(definitions) and len({item['id'] for item in schema}) == len(definitions)
def field(key,label,ko,type_,default,choices=None,when=None):
    result=dict(key=key,label=label,ko=ko,type=type_,default=default)
    if choices: result['choices']=[dict(id=id_,label=en,ko=ko_) for id_,en,ko_ in choices]
    if when: result['when']=when
    return result
def col(key,en,ko,default): return field(key,en,ko,'column',default)
def multi(key,en,ko): return field(key,en,ko,'columns','auto')

credible_fields=[field('level','Credible level','베이지안 구간 수준','number','0.95')]
bayesian_prior=[field('alpha','Prior α','사전 α','number','1'),field('beta','Prior β','사전 β','number','1')]
grouping=field('grouping','Grouping','그룹 구성','choice','columns',[('columns','Columns','열별 그룹'),('groups','Group / value columns','그룹·값 열')])
group_fields=[grouping,dict(multi('columns','Group columns','그룹 열'),when={'grouping':['columns']}),dict(col('group','Group column','그룹 열',0),when={'grouping':['groups']}),dict(col('value','Value column','값 열',1),when={'grouping':['groups']})]
survival_fields=[col('time','Time','시간 열',0),col('event','Event','사건 열',1),field('eventValue','Event value','사건 발생 값','number','1')]
cluster_fields=[col('subject','Subject / cluster','대상·군집 열',0),col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열')]
offset_fields=[field('adjustment','Offset / exposure','오프셋·노출량','choice','none',[('none','None','없음'),('offset','Log offset','로그 오프셋'),('exposure','Exposure','노출량')]),dict(col('offset','Offset / exposure column','오프셋·노출량 열',0),when={'adjustment':['offset','exposure']})]
count_fields=[col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열')]+offset_fields
forms={
    'shapiro':[col('column','Sample column','표본 열',0)],
    'tukey':group_fields,'gameshowell':group_fields,
    'pca':[multi('columns','Feature columns','변수 열'),field('components','Components','주성분 수','number','2'),field('standardize','Scaling','척도','choice','1',[('1','Standardize (sample SD)','표준화 (표본 표준편차)'),('0','Center only','중심화만')])],
    'bayesbootstrap':[field('layout','Data layout','자료 구성','choice','single',[('single','Single sample','단일 표본'),('columns','Two columns','두 컬럼'),('groups','Group / value columns','그룹·값 컬럼')]),
        dict(col('column','Sample column','표본 열',0),when={'layout':['single']}),
        dict(col('first','Group A column','A 그룹 열',0),when={'layout':['columns']}),dict(col('second','Group B column','B 그룹 열',1),when={'layout':['columns']}),
        dict(field('comparison','Comparison','비교 방식','choice','independent',[('independent','Independent samples','독립 표본'),('paired','Paired rows','대응 행')]),when={'layout':['columns']}),
        dict(col('group','Group column','그룹 열',0),when={'layout':['groups']}),dict(col('value','Value column','값 열',1),when={'layout':['groups']}),
        dict(field('order','Group A','A 그룹','choice','first',[('first','First observed group','먼저 나온 그룹'),('reverse','Second observed group','둘째로 나온 그룹')]),when={'layout':['groups']}),
        field('statistic','Statistic','통계량','choice','mean',[('mean','Mean','평균'),('median','Median (sample estimate / weighted posterior)','중앙값 (표본 추정치 / 가중 사후추출)'),('variance','Population variance','모분산'),('stdev','Population SD','모표준편차')])]+credible_fields+[field('samples','Posterior draws','사후 추출 수','number','10000'),field('seed','Seed','시드','number','0')],
    'ancova':[col('group','Group column','그룹 열',0),col('response','Response','종속변수 열',-1),multi('predictors','Covariates','공변량 열'),field('level','Confidence level','신뢰수준','number','0.95'),field('slopes','Slope homogeneity','회귀 기울기 동질성','choice','test',[('test','Test','검정'),('none','Skip','생략')])],
    'glm':[col('response','Response','반응변수 열',-1),multi('predictors','Predictors','설명변수 열'),field('family','Family','분포','choice','gaussian',[('gaussian','Gaussian','정규'),('binomial','Binomial (0/1)','이항 (0/1)'),('poisson','Poisson','포아송'),('gamma','Gamma','Gamma'),('inversegaussian','Inverse Gaussian','역가우스'),('nbinom','Negative binomial (NB2)','음이항 (NB2)')]),
        field('link','Link function','연결함수','choice','auto',[('auto','Default for family','분포별 기본값'),('identity','Identity','항등'),('log','Log','로그'),('logit','Logit','로짓'),('probit','Probit','프로빗'),('cloglog','Complementary log-log','상보 로그로그'),('inverse','Inverse','역수'),('inverse_squared','Inverse squared','역수 제곱')]),
        dict(field('dispersionMode','NB2 dispersion','NB2 과산포','choice','fixed',[('fixed','Fixed alpha','alpha 고정'),('estimate','Estimate by ML','ML 추정')]),when={'family':['nbinom']}),
        dict(field('alpha','NB2 alpha (fixed)','NB2 alpha (고정)','number','1'),when={'family':['nbinom'],'dispersionMode':['fixed']})]+offset_fields,
    'bayesproportion':[field('layout','Data','자료 형태','choice','binary',[('binary','Binary observations (0/1)','0/1 관측값'),('counts','Successes / trials','성공 수·시행 수')]),
        dict(col('column','Observation column','관측값 열',0),when={'layout':['binary']}),
        dict(col('successes','Successes','성공 수 열',0),when={'layout':['counts']}),dict(col('trials','Trials','시행 수 열',1),when={'layout':['counts']})]+bayesian_prior+credible_fields+[field('threshold','Threshold p0','기준 비율 p0','number','0.5')],
    'bayesmean':[col('column','Sample column','표본 열',0),field('mu','Prior mean μ0','사전 평균 μ0','number','0'),field('kappa','Prior strength κ0','사전 강도 κ0','number','1'),field('alpha','Variance prior α0','분산 사전 α0','number','2'),field('beta','Variance prior β0','분산 사전 β0','number','1')]+credible_fields+[field('threshold','Threshold mean','기준 평균','number','0')],
    'bayescompare':[col('first','Group A column','A 집단 열',0),col('second','Group B column','B 집단 열',1),
        field('variance','Variance model','분산 모형','choice','equal',[('equal','Equal variance','등분산'),('unequal','Unequal variance','이분산')]),
        field('mu','Prior mean μ0 (both groups)','사전 평균 μ0 (두 집단)','number','0'),field('kappa','Prior strength κ0','사전 강도 κ0','number','0.01'),
        field('alpha','Variance prior α0','분산 사전 α0','number','2'),field('beta','Variance prior β0','분산 사전 β0','number','1')]+credible_fields+[
        field('samples','Posterior draws','사후 추출 수','number','20000'),field('seed','Simulation seed','시뮬레이션 시드','number','0')],
    'bayesrate':[field('layout','Data','자료 형태','choice','counts',[('counts','Counts (exposure = 1)','횟수 (노출량 = 1)'),('exposure','Counts / exposure','횟수·노출량')]),
        col('column','Count column','횟수 열',0),dict(col('exposure','Exposure','노출량 열',1),when={'layout':['exposure']}),field('alpha','Prior shape α','사전 shape α','number','1'),field('beta','Prior rate β','사전 rate β','number','1')]+credible_fields+[field('threshold','Threshold rate','기준 발생률','number','1')],
    'padjust':[col('column','p-value column','p값 열',0),field('method','Correction','보정 방법','choice','holm',[('bonferroni','Bonferroni','Bonferroni'),('holm','Holm','Holm'),('fdr','FDR (BH)','FDR (BH)')]),field('alpha','Significance α','유의수준 α','number','0.05')],
    'levene':group_fields,'bartlett':group_fields,
    'mcnemar':[field('layout','Data','자료 형태','choice','counts',[('counts','2×2 counts','2×2 빈도표'),('pairs','Paired observations','대응 관측값')]),col('first','Before / first','이전·첫째 열',0),col('second','After / second','이후·둘째 열',1),field('method','Method','검정 방법','choice','exact',[('asymptotic','McNemar','McNemar'),('exact','Exact McNemar','Exact McNemar'),('corrected','Continuity correction','연속성 보정')])],
    'kaplanmeier':survival_fields+[field('level','Confidence level','신뢰수준','number','0.95')],
    'logrank':survival_fields+[col('group','Group','그룹 열',2)],
    'survivalanalysis':survival_fields+[field('grouping','Groups','그룹','choice','groups',[('groups','Group column','그룹 열'),('all','All subjects','전체 대상')]),
        dict(col('group','Group column','그룹 열',2),when={'grouping':['groups']}),
        field('cox','Cox model','Cox 모형','choice','0',[('0','Off','끔'),('1','On','켬')]),
        dict(multi('predictors','Cox predictors','Cox 설명변수 열'),when={'cox':['1']}),
        field('ties','Tie handling','동률 처리','choice','efron',[('efron','Efron','Efron'),('breslow','Breslow','Breslow')]),
        dict(field('ph','Proportional-hazards check','비례위험 검정','choice','test',[('test','Schoenfeld test','Schoenfeld 검정'),('none','Skip','생략')]),when={'cox':['1']})],
    'cox':survival_fields+[multi('predictors','Predictors','설명변수 열'),
        field('ties','Tie handling','동률 처리','choice','efron',[('efron','Efron','Efron'),('breslow','Breslow','Breslow')]),
        field('truncation','Left truncation','좌측 절단','choice','none',[('none','None','없음'),('entry','Entry-time column','진입시간 열')]),
        dict(col('entry','Entry time','진입시간 열',2),when={'truncation':['entry']}),
        field('ph','Proportional-hazards check','비례위험 검정','choice','test',[('test','Schoenfeld test','Schoenfeld 검정'),('none','Skip','생략')])],
    'repeatedanova':[multi('columns','Condition columns','조건 열'),field('factor2','Second-factor levels','둘째 요인 수준','number','1')],
    'mixedmodel':cluster_fields+[field('slope','Random-slope predictors','랜덤 기울기 변수','number','0'),field('method','Estimation','추정 방법','choice','reml',[('reml','REML','REML'),('ml','ML','ML')]),field('ci','Fixed-effect 95% CI','고정효과 95% 신뢰구간','choice','wald',[('wald','Wald','Wald'),('profile','ML profile likelihood','ML 프로파일 우도'),('bootstrap','Parametric bootstrap','모수적 부트스트랩')]),dict(field('ciSamples','Bootstrap refits','부트스트랩 재적합 수','number','200'),when={'ci':['bootstrap']}),dict(field('ciSeed','Bootstrap seed','부트스트랩 시드','number','0'),when={'ci':['bootstrap']})],
    'glmm':cluster_fields+[field('slope','Random slope position (0 = none, Laplace)','랜덤 기울기 번호 (0 = 없음, Laplace)','number','0'),field('family','Family','분포','choice','binomial',[('binomial','Binomial (0/1)','이항 (0/1)'),('poisson','Poisson','포아송'),('nbinom','Negative binomial (NB2)','음이항 (NB2)')]),dict(field('points','Quadrature points (1 = Laplace)','적분 점 수 (1 = Laplace)','number','15'),when={'slope':['0']}),dict(field('sensitivity','Quadrature sensitivity','적분 민감도 확인','choice','likelihood',[('likelihood','Compare likelihood','우도 비교'),('refit','Refit and compare coefficients','재적합·계수 비교')]),when={'slope':['0']})]+[dict(f,when={**f.get('when',{}),'family':['poisson','nbinom']}) for f in offset_fields],
    'poissonreg':count_fields,'nbreg':count_fields,
    'gee':cluster_fields+[field('family','Family','분포','choice','gaussian',[('gaussian','Gaussian','Gaussian'),('binomial','Binomial (0/1)','이항 (0/1)'),('poisson','Poisson','포아송')]),field('corr','Working correlation','작업상관','choice','independence',[('independence','Independent','독립'),('exchangeable','Exchangeable','교환가능'),('ar1','AR(1)','AR(1)')]),field('correction','Covariance correction','공분산 보정','choice','robust',[('robust','Asymptotic sandwich','점근 샌드위치'),('small','Mancl-DeRouen + t','Mancl-DeRouen + t')]),field('interactions','Interactions (columns or names)','상호작용 (열·이름)','number','')],
    'kstest':[field('mode','Samples / distribution','표본·분포','choice','two',[('two','Two samples','두 표본'),('normal','Normal','정규분포'),('uniform','Uniform','균등분포')]),col('first','Sample column','표본 열',0),dict(col('second','Second sample','둘째 표본 열',1),when={'mode':['two']}),dict(field('location','Mean / lower bound','평균·하한','number','0'),when={'mode':['normal','uniform']}),dict(field('scale','SD / width','표준편차·폭','number','1'),when={'mode':['normal','uniform']})],
    'impute':[field('method','Method','대체 방법','choice','mean',[('mean','Mean','평균'),('median','Median','중앙값'),('mode','Mode','최빈값'),('regression','Regression','회귀'),('knn','k-NN','k-NN')]),dict(field('k','Neighbours','이웃 수','number','5'),when={'method':['knn']})],
    'crossvalidate':[field('folds','Folds','폴드 수','number','3'),field('seed','Seed','시드','number','0'),
        field('split','Split','분할','choice','random',[('random','Random','무작위'),('blocked','Blocked','블록'),('stratified','Stratified','층화')]),
        field('model','Model','모형','choice','linear',[('linear','Linear (OLS)','선형 (OLS)'),('ridge','Ridge','Ridge'),('lasso','Lasso','Lasso'),('elasticnet','Elastic net','Elastic net'),('logistic','Logistic (0/1)','로지스틱 (0/1)')]),
        dict(field('alpha','Penalty α','벌점 α','number','0.1'),when={'model':['ridge','lasso','elasticnet','logistic']}),
        dict(field('ratio','L1 ratio','L1 비율','number','0.5'),when={'model':['elasticnet']})]
}
design_field=field('design','Study design','연구 설계','choice','independent',[('independent','Independent groups','독립 두 그룹'),('paired','Paired observations','대응 표본'),('onesample','One sample','단일 표본')])
tail_field=field('tail','Alternative hypothesis','대립가설','choice','two',[('two','Two-sided','양측'),('greater','Greater','우측'),('less','Less','좌측')])
forms.update({
    'twowayanova':[field('layout','Data layout','자료 구조','choice','groups',[('groups','Factor columns + response','요인 열 + 반응 열'),('columns','One column per factor cell','요인 조합별 열')]),dict(col('factorA','Factor A','요인 A 열',0),when={'layout':['groups']}),dict(col('factorB','Factor B','요인 B 열',1),when={'layout':['groups']}),dict(col('response','Response','반응 열',2),when={'layout':['groups']}),dict(multi('columns','Cell columns (A slowest)','조합별 열 (A 최외곽)'),when={'layout':['columns']}),dict(field('levelsB','Factor B levels','요인 B 수준 수','number','2'),when={'layout':['columns']}),field('interaction','Model','모형','choice','1',[('1','Main effects + interaction','주효과 + 상호작용'),('0','Additive main effects','가법 주효과')])],
    'friedman':[multi('columns','Condition columns','반복 조건 열')],
    'cohend':[col('first','Group A','그룹 A 열',0),col('second','Group B','그룹 B 열',1),field('design','Comparison','비교 방식','choice','independent',[('independent','Independent groups','독립 표본'),('paired','Paired observations','대응 표본')])],
    'eta2':group_fields,
    'bootstrapci':[col('column','Data column','자료 열',0),field('statistic','Statistic','통계량','choice','mean',[(v,en,ko) for v,en,ko in [('mean','Mean','평균'),('median','Median','중앙값'),('stdev','Standard deviation','표준편차')]]),field('level','Confidence level','신뢰수준','number','0.95'),field('samples','Resamples','재표집 수','number','2000'),field('seed','Seed','시드','number','0')],
    'kmeans':[multi('columns','Feature columns','변수 열'),field('clusters','Clusters','군집 수','number','2'),field('seed','Seed','시드','number','0')],
    'multinomial':[col('response','Category response','범주 반응 열',-1),multi('predictors','Predictors','설명변수 열')],
    'ordinal':[col('response','Ordered category response','순서형 반응 열',-1),multi('predictors','Predictors','설명변수 열')],
    'testpower':[design_field,field('effect','Cohen’s d','Cohen의 d','number','0.5'),field('n','Sample size per group / pairs','그룹별 표본수·쌍 수','number','64'),field('alpha','Significance level α','유의수준 α','number','0.05'),tail_field],
    'samplesize':[design_field,field('effect','Cohen’s d','Cohen의 d','number','0.5'),field('power','Target power','목표 검정력','number','0.8'),field('alpha','Significance level α','유의수준 α','number','0.05'),tail_field],
})
forms['linearmodel']=[col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열'),multi('categorical','Categorical predictors','범주 설명변수 열'),field('order','Interaction order (1 = additive)','상호작용 차수 (1 = 가법)','number','2'),field('ssType','Sums of squares','제곱합','choice','3',[('3','Type III','Type III'),('2','Type II','Type II')]),field('coding','Categorical coding','범주 코딩','choice','sum',[('sum','Sum-to-zero contrasts','합이 0인 대비'),('treatment','Treatment / dummy coding','처리 / 더미 코딩')])]
forms['crossvalidate']=[col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열')]+forms['crossvalidate']
two_fields=[grouping,dict(col('first','Group A column','A 그룹 열',0),when={'grouping':['columns']}),dict(col('second','Group B column','B 그룹 열',1),when={'grouping':['columns']}),dict(col('group','Group column','그룹 열',0),when={'grouping':['groups']}),dict(col('value','Value column','값 열',1),when={'grouping':['groups']}),field('firstGroup','Group A value','A 그룹 값','group','',when={'grouping':['groups']}),field('secondGroup','Group B value','B 그룹 값','group','',when={'grouping':['groups']})]
matching_fields=[field('matching','Pair matching','대응 연결','choice','order',[('order','Within-group row order','그룹 안의 행 순서'),('subject','Subject ID','대상 ID')],when={'grouping':['groups']}),dict(col('subject','Subject ID','대상 ID 열',0),when={'grouping':['groups'],'matching':['subject']})]
forms['cohend']=two_fields+forms['cohend'][2:]+matching_fields
forms['bayescompare']=two_fields+forms['bayescompare'][2:]
for id_ in ('friedman','repeatedanova'): forms[id_]=group_fields+matching_fields+forms[id_][1:]
# KS one-sample keeps its own data column; two-sample enables arbitrary long roles.
forms['kstest']+= [dict(f,when={**f.get('when',{}),'mode':['two']}) for f in two_fields if f['key'] not in ('first','second')]
forms['bayesbootstrap']=[dict(f,when={'layout':['columns','groups']}) if f['key']=='comparison' else f for f in forms['bayesbootstrap']]
forms['bayesbootstrap'] += [dict(f,when={**{k:v for k,v in f.get('when',{}).items() if k!='grouping'},'layout':['groups']}) for f in two_fields+matching_fields if f['key'] in ('firstGroup','secondGroup','matching','subject')]
next(f for f in forms['mcnemar'] if f['key']=='layout')['choices'].append(dict(id='groups',label='Group / value columns',ko='그룹·값 열'))
forms['mcnemar'] += [dict(f,when={**{k:v for k,v in f.get('when',{}).items() if k!='grouping'},'layout':['groups']}) for f in two_fields+matching_fields if f['key'] in ('group','value','firstGroup','secondGroup','matching','subject')]
form_help={
 'shapiro':('Choose a numeric sample column to test normality. Interpret the p value with the Q–Q plot alongside the sample distribution.','정규성을 검정할 숫자 표본 열을 선택합니다. p값은 Q–Q plot·표본 분포와 함께 해석합니다.'),
 'tukey':('Select independent group columns or group/value columns. Tukey–Kramer compares all pairs assuming equal variances and reports adjusted p values and 95% simultaneous intervals.','독립 그룹 열 또는 그룹·값 열을 선택합니다. Tukey–Kramer는 등분산을 가정하여 모든 쌍의 보정 p값·95% 동시 구간을 제공합니다.'),
 'gameshowell':('Select independent group columns or group/value columns. Games–Howell compares all pairs allowing unequal variances and reports adjusted p values and 95% simultaneous intervals.','독립 그룹 열 또는 그룹·값 열을 선택합니다. Games–Howell은 이분산을 허용하여 모든 쌍의 보정 p값·95% 동시 구간을 제공합니다.'),
 'linearmodel':('Choose response and predictors; mark only the categorical predictors. Numeric predictors retain their units. Interaction order 1 is additive, 2 includes all pairs, 3 all triples, etc. Choose Type II or III and sum or dummy coding; Type III factorial interactions require sum contrasts. Coefficient intervals and joint term F tests share the same OLS fit. Numeric main effects with interactions refer to zero: center covariates when appropriate.','반응·설명변수를 선택하고 그중 범주 변수만 표시합니다. 숫자 변수는 원래 단위를 사용합니다. 차수 1은 가법, 2는 모든 쌍, 3은 모든 삼중 상호작용 등을 포함합니다. Type II/III·합 대비/더미 코딩을 선택하며 Type III 요인 상호작용에는 합 대비가 필요합니다. 계수 구간·항별 부분 F 검정은 같은 OLS 적합을 사용합니다. 숫자 상호작용의 주효과는 0 기준이므로 필요하면 공변량을 중심화하세요.'),
 'twowayanova':('Select two categorical factor columns and the numeric response, or columns for every factor cell ordered with A slowest. Observations must be independent; repeated measures belong in Repeated-measures ANOVA. Type III sum contrasts handle unequal cell sizes. Replication and identifiable cells are required for the interaction model. Results include residual diagnostics and cell mean intervals.','두 범주 요인 열·숫자 반응 열 또는 A를 최외곽으로 정렬한 요인 조합별 열을 선택합니다. 관측은 독립이어야 하며 반복측정은 반복측정 ANOVA를 사용하세요. 합이 0인 대비의 Type III 검정으로 불균형 셀 크기를 처리합니다. 상호작용 모형에는 반복 관측·식별 가능한 셀이 필요합니다. 잔차 진단과 셀 평균 구간을 함께 제공합니다.'),
 'friedman':('Choose at least three numeric condition columns. Each row must be the same subject across conditions; incomplete selected rows are rejected. Tie-corrected chi-square approximation; small-sample p values can be inaccurate.','숫자 반복 조건 열을 3개 이상 선택합니다. 각 행은 모든 조건에서 같은 대상이어야 하며 선택 열의 불완전한 행은 거부합니다. 동점 보정 χ² 근사이며 소표본 p값은 부정확할 수 있습니다.'),
 'cohend':('Select two columns and independent or paired comparison. Paired analysis uses complete rows; independent samples omit blank cells separately.','두 열과 독립·대응 비교를 선택합니다. 대응 분석은 완전한 행을, 독립 분석은 각 열의 빈 셀을 별도로 제외합니다.'),
 'eta2':('Compare two or more groups. Choose group columns or a group/value layout.','두 개 이상 그룹을 비교합니다. 열별 그룹 또는 그룹·값 열을 선택하세요.'),
 'bootstrapci':('Choose a column and statistic, confidence level, resamples and seed. IID percentile bootstrap.','자료 열·통계량·신뢰수준·재표집 수·시드를 선택합니다. IID 백분위 부트스트랩입니다.'),
 'kmeans':('Select numeric feature columns, cluster count and seed. Uses raw feature scales, Euclidean distance and ten restarts; scale features appropriately. Plot axes show selected original features.','숫자 변수 열·군집 수·시드를 선택합니다. 원래 척도의 유클리드 거리와 10회 초기화를 사용하므로 변수 척도를 확인하세요. 그래프 축은 선택한 원래 변수입니다.'),
 'multinomial':('Choose numeric category response and predictors. Smallest category is the reference.','숫자 범주 반응 열과 설명변수를 선택합니다. 가장 작은 범주가 기준입니다.'),
 'ordinal':('Choose ordered numeric category response and predictors. Category order follows numeric order; proportional odds are assumed.','순서가 있는 숫자 범주 반응 열과 설명변수를 선택합니다. 숫자 순서를 사용하며 비례오즈를 가정합니다.'),
 'testpower':('Set effect size, sample size, design, significance level and alternative. n is per group for independent samples, number of pairs for paired data.','효과크기·표본수·연구 설계·유의수준·대립가설을 설정합니다. n은 독립 표본의 그룹별 수 또는 대응 표본의 쌍 수입니다.'),
 'samplesize':('Set effect size and target power before collecting data. Returned n is per group or the number of pairs, according to the selected design.','자료 수집 전에 효과크기·목표 검정력을 설정합니다. 산출된 n은 설계에 따라 그룹별 수 또는 대응 쌍 수입니다.'),
 'pca':('Choose numeric feature columns, components and sample-SD standardization or centering only. Selected rows must be complete. Scree plot includes all components; score and loading plots use the retained components. Loading arrows show eigenvector coefficients, on separate axes from scores.','숫자 변수 열·주성분 수·표본 표준편차 표준화 또는 중심화를 선택합니다. 선택한 열의 모든 행이 완전해야 합니다. 설명분산 그래프는 모든 주성분을, 점수·로딩 그래프는 유지한 주성분을 표시합니다. 로딩 화살표는 고유벡터 계수이며 점수와 별도 좌표를 사용합니다.'),
 'bayesbootstrap':('Single sample or statistic(B) − statistic(A). Independent columns omit blanks separately; paired columns require complete matching rows and share Dirichlet weights (difference of marginal statistics, not the statistic of row differences). Group/value columns require exactly two labels and complete selected rows; choose which observed group is A. Dirichlet(1,…,1) weights; equal-tailed posterior credible interval, P(difference > 0), P(difference < 0), P(difference = 0) and histogram. Median estimate is the ordinary sample median (average the two middle values for even n); posterior draws use the Lower weighted quantile (smallest value with weighted CDF >= 0.5); variance/SD use population weights. Seed makes draws reproducible.','단일 표본 또는 통계량(B) − 통계량(A)을 분석합니다. 독립 컬럼은 빈 셀을 각각 제외하며, 대응 컬럼은 완전한 같은 행에 공통 Dirichlet 가중치를 적용합니다(각 컬럼 통계량의 차이이며, 행별 차이의 통계량과 다릅니다). 그룹·값 컬럼은 두 그룹과 완전한 선택 행이 필요하며 먼저·둘째로 나온 그룹 중 A를 선택합니다. Dirichlet(1,…,1) 가중치·등꼬리 사후 구간·P(차이 > 0)·P(차이 < 0)·P(차이 = 0)·히스토그램. 중앙값 estimate는 일반 표본 중앙값(짝수 표본은 가운데 두 값의 평균)이고 사후추출은 Lower weighted quantile(가중 누적확률이 0.5 이상인 최소값), 분산·SD는 모집단 가중치 기준이며 시드로 재현합니다.'),
 'bayescompare':('Independent groups; B - A. Blank cells are omitted separately in each selected column, so sample sizes may differ. Choose proper NIG priors in your measurement units. BF compares H1 with its conditioned point null. Simulation intervals and MCSE are labeled.','독립 두 집단; 차이는 B - A. 선택한 각 열의 빈 셀은 독립적으로 제외하므로 표본수가 달라도 됩니다. 측정 단위에 맞게 NIG 사전분포를 지정하세요. BF는 H1과 이를 조건부 제한한 점귀무 모형을 비교합니다. 시뮬레이션 구간·MCSE를 표시합니다.'),
 'ancova':('Compare groups after adjusting for selected covariates. Text group labels are accepted. Type II tests, adjusted means, and optional slope homogeneity check.','선택한 공변량을 보정하여 그룹을 비교합니다. 문자 그룹도 사용할 수 있습니다. Type II 검정·조정 평균·선택적 기울기 동질성 검정.'),
 'glm':('Choose a family, its link, response and predictors. Binomial uses 0/1; counts use nonnegative integers; Gamma/inverse Gaussian use positive responses. Exposure requires a log link. NB2 dispersion: fixed alpha or joint ML estimation.','분포·연결함수·반응변수·설명변수를 선택하세요. 이항은 0/1, 빈도는 음이 아닌 정수, Gamma·역가우스는 양수입니다. 노출량은 로그 연결에서만 사용합니다. NB2 과산포는 alpha 고정 또는 공동 ML 추정을 선택합니다.'),
 'bayesproportion':('Beta prior → posterior proportion · credible interval · P(p > p0). BF10: Beta alternative / point null p=p0.','Beta 사전 → 사후 비율 · 베이지안 구간 · P(p > p0). BF10: Beta 대립 / p=p0 점귀무.'),
 'bayesmean':('Normal data, unknown variance. Adjust the normal-inverse-gamma prior to your data scale; mean interval and next-observation prediction.','분산 미지의 정규 자료. 자료 척도에 맞춰 정규-역감마 사전을 조절하세요. 평균 구간·다음 관측 예측.'),
 'bayesrate':('Gamma prior → Poisson rate · credible interval · P(rate > threshold). β is the rate parameter.','Gamma 사전 → 포아송 발생률 · 베이지안 구간 · 기준 초과 확률. β는 rate(척도의 역수)입니다.'),
 'padjust':('Adjust p values from the selected column.','선택한 열의 p값을 보정합니다.'),
 'levene':('Compare group variances using median centers.','중앙값 기준으로 그룹의 분산을 비교합니다.'),
 'bartlett':('Compare variances of normally distributed groups.','정규분포를 가정해 그룹의 분산을 비교합니다.'),
 'mcnemar':('Use two paired category columns or a 2×2 count table. Choose McNemar (uncorrected chi-square), Exact McNemar (two-sided binomial), or continuity correction. Results show the number of discordant pairs (b + c) and the selected method’s p value together.','두 대응 범주 열 또는 2×2 빈도표를 사용합니다. McNemar(보정 없는 χ²), Exact McNemar(양측 이항), 연속성 보정을 선택합니다. 결과에 불일치 쌍의 수(b + c)와 선택한 방법의 p값을 함께 표시합니다.'),
 'kaplanmeier':('Choose time and event columns; other event values are censored.','시간·사건 열을 선택합니다. 발생 값 이외는 중도절단입니다.'),
 'logrank':('Compare exactly two groups; other event values are censored.','두 그룹을 비교합니다. 발생 값 이외는 중도절단입니다.'),
 'survivalanalysis':('Kaplan–Meier curves · log-rank · Cox; other event values are censored.','Kaplan–Meier 곡선 · log-rank · Cox. 발생 값 이외는 중도절단입니다.'),
 'cox':('Proportional hazards; Breslow/Efron ties, optional entry column for left truncation and a scaled-Schoenfeld PH check.','비례위험; Breslow/Efron 동률, 선택적 진입시간 열(좌측 절단), 스케일된 Schoenfeld PH 검정.'),
 'repeatedanova':('One row per subject; one or two within factors with GG corrections.','행마다 한 대상. 일·이요인 반복측정·GG 보정입니다.'),
 'mixedmodel':('Gaussian random intercept + up to three random slopes; REML (default) / ML, singular-fit diagnostics and subject BLUPs. ICC for random slopes is at x=0. Slopes: 0, a position or 1,2. Wald z inference. Optional fourth argument: profile (ML fixed-effect profile CI) or [bootstrap,200,0] (parametric fixed-effect percentile CI); alternative CI omit Wald p-values. Reports logLik/AIC/BIC; compare REML criteria only with identical fixed effects and data. Nonconverged fits withhold Wald inference.','Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기; REML(기본)·ML, singular 진단·대상별 BLUP. 기울기 모형의 ICC는 x=0 기준. 기울기: 0, 번호 또는 1,2. Wald z 추론. 선택적 넷째 인수: profile(ML 고정효과 프로파일 구간), [bootstrap,200,0](모수적 고정효과 백분위 구간); 대안 구간은 Wald p값을 생략합니다. logLik/AIC/BIC 제공; REML 비교는 같은 고정효과·자료에서만 가능합니다. 미수렴 적합의 Wald 추론은 표시하지 않습니다.'),
 'glmm':('Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial / Poisson / NB2. ML quadrature (15 default, 1 Laplace, 7-31); conditional effects. Count families support log offset or positive exposure. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.','랜덤 절편 또는 절편 + 상관 기울기 1개. 선택적 일곱째 인수: 선택한 설명변수 번호(0 없음). 기울기는 Laplace(셋째 인수 1), 3개 이상 대상 내 설명변수 변화가 필요합니다. ICC는 x=0 기준이며 singular·식별 불가 적합은 Wald 추론을 생략합니다. 이항·포아송·NB2. ML 적분(기본 15점, 1 Laplace, 7~31); 조건부 효과. 빈도 분포는 로그 오프셋·양수 노출량 지원. 소수 대상의 Wald 추론은 부정확할 수 있습니다. Laplace·31점도 다른 적분점의 우도를 비교합니다. 선택적 여섯째 인수 refit은 재적합 계수를 비교하고 0.1 SE 초과 변동이면 CI·p값을 생략합니다. 오프셋이 없으면 refit 앞에 [],offset을 사용합니다.'),
 'poissonreg':('Log-link counts; choose predictors, response and optional offset / positive exposure.','로그 연결 빈도 모형; 설명변수·반응·선택적 오프셋·양수 노출량.'),
 'nbreg':('NB2 counts with estimated dispersion; optional offset / positive exposure.','과산포를 추정하는 NB2 빈도 모형; 선택적 오프셋·양수 노출량.'),
 'gee':('Working correlation independence / exchangeable / AR(1); dispersion-adjusted correlation and cluster-robust SE. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. Review the cluster count when interpreting intervals. AR(1): row order, equal spacing. Interactions accept header names (age,weight), the shown column letters or labels (y,z / age (y),weight (z)), column numbers (2,3) or predictor order (p1,p2); separate pairs with ;.','작업상관 independence / exchangeable / AR(1); 분산 보정 상관·군집 강건 표준오차. 선택적 다섯째 인수 small은 Mancl-DeRouen 공분산·군집 수-계수 수 자유도의 t 추론을 적용합니다. 상호작용이 없으면 넷째 인수는 []입니다. 구간 해석 시 군집 수를 확인하세요. AR(1): 행 순서·등간격. 상호작용은 열 이름(age,weight), 표시된 열 문자·라벨(y,z / age (y),weight (z)), 열 번호(2,3), 설명변수 순서(p1,p2)로 입력하고 쌍은 ;로 구분합니다.'),
 'kstest':('Compare two samples or a specified continuous distribution.','두 표본 또는 지정한 연속분포와 비교합니다.'),
 'impute':('Analyze missing numeric cells by mean, median, mode, regression or k-NN; Apply to current data writes the replacements while retaining observed values and headers. Reanalyze after editing data.','숫자 자료의 결측값을 평균·중앙값·최빈값·회귀·k-NN으로 분석합니다. 현재 데이터에 적용하면 관측값·헤더를 유지하며 결측 셀을 실제로 대체합니다. 편집 후에는 다시 분석하세요.'),
 'crossvalidate':('Held-out folds fitted on training rows only; choose split, model and penalty.','훈련 행으로만 적합하는 홀드아웃 폴드; 분할·모형·벌점을 선택합니다.')
}
def literal(node):
    if isinstance(node,ast.Name) and node.id=='NA': return 'NA'
    if isinstance(node,(ast.List,ast.Tuple)): return [literal(element) for element in node.elts]
    return ast.literal_eval(node)


proportion_tail=field('tail','Alternative','대립가설','choice','both',[('both','Two-sided','양측'),('left','Less than','작다 (좌측)'),('right','Greater than','크다 (우측)')])
forms['tinterval']=[col('column','Sample column','표본 열',0),field('level','Confidence level (%)','신뢰수준 (%)','number','95')]
forms['zinterval']=forms['tinterval']+[field('sigma','Known population SD σ','알려진 모집단 표준편차 σ','number','2')]
form_help['tinterval']=('Select a sample column and confidence level. Uses the sample SD and Student t distribution for the mean interval.', '표본 열과 신뢰수준을 선택합니다. 표본 표준편차와 Student t 분포로 평균 구간을 계산합니다.')
form_help['zinterval']=('Select a sample column and confidence level; enter the known population SD.', '표본 열·신뢰수준을 선택하고 알려진 모집단 표준편차를 입력합니다.')
definitions['tinterval']['example']='tinterval(95,[1,2,3,4,5])'
definitions['zinterval']['example']='zinterval(95,2,[1,2,3,4,5])'
proportion_layout=field('layout','Data layout','자료 구성','choice','counts',[('counts','Successes / trials','성공 수·시행 수'),('binary','Binary observations','이항 관측값')])
proportion_counts=[dict(col('successes','Successes column','성공 수 열',0),when={'layout':['counts']}),dict(col('trials','Trials column','시행 수 열',1),when={'layout':['counts']})]
forms['propztest']=[proportion_layout]+proportion_counts+[dict(col('column','Sample column','표본 열',0),when={'layout':['binary']}),dict(field('successValue','Success value','성공 값','text','1'),when={'layout':['binary']}),field('p0','Null proportion p0','귀무가설 비율 p0','number','0.5'),proportion_tail]
forms['propztest2']=[proportion_layout]+proportion_counts+[dict(f,when={**f.get('when',{}),'layout':['binary']}) for f in two_fields]+[dict(field('successValue','Success value','성공 값','text','1'),when={'layout':['binary']}),proportion_tail]
form_help['propztest']=('Counts: rows are success/trial batches for one population. Binary data: select a sample column and success value. H0: p=p0; p0 must be between 0 and 1. Expected successes and failures below 10 flag unreliable normal approximation.', '빈도 입력의 각 행은 같은 모집단의 성공 수·시행 수입니다. 이항 자료는 표본 열·성공 값을 선택합니다. H0: p=p0, p0는 0과 1 사이입니다. 기대 성공·실패 수가 10 미만이면 정규근사 주의를 표시합니다.')
form_help['propztest2']=('Counts: exactly two rows, Group A then Group B. Binary data: independent columns or group/value columns with a common success value. H0: pA=pB; left/right alternatives refer to A−B. Uses the pooled null proportion; paired outcomes require McNemar.', '빈도 입력은 정확히 두 행이며 A 그룹·B 그룹 순서입니다. 이항 자료는 독립된 두 열 또는 그룹·값 열과 공통 성공 값을 선택합니다. H0: pA=pB, 좌측·우측은 A−B 기준입니다. 귀무가설의 합동 비율을 사용하며 대응 결과는 McNemar를 사용합니다.')
definitions['propztest']['example']='propztest(0.5,[[60,100]])'
definitions['propztest2']['example']='propztest2(60,100,45,100)'
forms.update(social_forms(field,col,multi,group_fields))
for spec in SOCIAL_SPECS:
    form_help[spec[0]]=(spec[6],spec[7])
    USES[spec[0]]=(spec[1]+'.',spec[2]+'.')
for item in schema:
    if item['id'] in ('propztest','propztest2','tinterval','zinterval'): item['example']=definitions[item['id']]['example']
    if item['id'] not in forms: continue
    item['controls']=forms[item['id']]
    item['formHelp'],item['formHelpKo']=form_help[item['id']]
    if item['id'] in ('mixedmodel','gee','glmm'):
        starts={'mixedmodel':('Optional fourth argument:', '선택적 넷째 인수:'), 'glmm':('Few-subject Wald', '소수 대상의 Wald'), 'gee':('Optional fifth argument', '선택적 다섯째 인수')}
        first,second=starts[item['id']]
        extra=item['formHelp'][item['formHelp'].index(first):]
        extra_ko=item['formHelpKo'][item['formHelpKo'].index(second):]
        if item['id']=='gee':
            extra=extra.split(' AR(1):')[0]
            extra_ko=extra_ko.split(' AR(1):')[0]
        item['help'] += ' ' + extra
        item['helpKo'] += ' ' + extra_ko
    arguments=ast.parse(item['example'],mode='eval').body.args
    first=literal(arguments[0])
    if item['id'] in ('tinterval','zinterval'): rows=[[v] for v in literal(arguments[-1])]
    elif item['id']=='propztest': rows=literal(arguments[1])
    elif item['id']=='propztest2': rows=[[60,100],[45,100]]
    elif item['input']=='none': rows=[]
    elif item['id'] in ('shapiro','padjust','bayesproportion','bayesmean','bayesrate','bayesbootstrap','bootstrapci'): rows=[[v] for v in first]
    elif item['id'] in ('tukey','gameshowell','levene','bartlett','kstest','bayescompare','cohend','eta2'):
        samples=[literal(arg) for arg in (arguments[:2] if item['id'] in ('bayescompare','cohend') else arguments)]; rows=[[sample[i] if i<len(sample) else '' for sample in samples] for i in range(max(map(len,samples)))]
    elif item['id']=='logrank': rows=[r+[i+1] for i,arg in enumerate(arguments) for r in literal(arg)]
    elif item['id']=='dunn': rows=[[sample[i] if i<len(sample) else '' for sample in first] for i in range(max(map(len,first)))]
    else: rows=first
    item['exampleRows']=[[str(v) for v in row] for row in rows]
    if item['id'] in ('cfa','sem'):
        # Every multi-group preset has a dedicated group column followed by the
        # same six indicators, so factor IDs continue to refer to those six.
        item['ordinalExampleCuts']=[2,3,4]
        item['multiGroupExampleIds']=['1','2']
    if item['id']=='glm':
        links={'identity':['gaussian'],'log':['gaussian','poisson','gamma','inversegaussian','nbinom'],'logit':['binomial'],'probit':['binomial'],'cloglog':['binomial'],'inverse':['gamma'],'inverse_squared':['inversegaussian']}
        for choice in next(f for f in item['controls'] if f['key']=='link')['choices']:
            if choice['id']!='auto': choice['when']={'family':links[choice['id']]}
(ROOT/'tests/fixtures').mkdir(exist_ok=True)
cases=[
 dict(id='propztest',rows=[['yes'],['no'],['yes']],settings=dict(layout='binary',successValue='yes',p0='0.4',tail='right'),expected='propztest(0.4,[1,0,1],right)'),
 dict(id='propztest',rows=[['60','100'],['12','20']],settings={},expected='propztest(0.5,[[60,100],[12,20]])'),
 dict(id='propztest2',rows=[['60','100'],['45','100']],settings=dict(tail='left'),expected='propztest2(60,100,45,100,left)'),
 dict(id='propztest2',rows=[['yes','no'],['yes','yes'],['no','']],settings=dict(layout='binary',successValue='yes'),expected='propztest2([1,1,0],[0,1])'),
 dict(id='propztest2',rows=[['A','yes'],['B','no'],['A','no'],['B','no']],settings=dict(layout='binary',grouping='groups',successValue='yes'),expected='propztest2([1,0],[0,0])'),
 dict(id='tinterval',rows=[['unused','1'],['ignored','2'],['','4']],settings=dict(column='1',level='90'),expected='tinterval(90,[1,2,4])'),
 dict(id='zinterval',rows=[['1'],['2'],['4']],settings=dict(level='99',sigma='3'),expected='zinterval(99,3,[1,2,4])'),
 dict(id='mcnemar',rows=[['20','8'],['2','15']],settings=dict(method='asymptotic'),expected='mcnemar([[20,8],[2,15]],asymptotic)'),
 dict(id='shapiro',rows=[['unused','1'],['','2'],['x',''],['y','4']],settings=dict(column='1'),expected='shapiro([1,2,4])'),
 dict(id='tukey',rows=[['A','1'],['B','4'],['A','2'],['B','5']],settings=dict(grouping='groups',group='0',value='1'),expected='tukey([1,2],[4,5])'),
 dict(id='gameshowell',rows=[['unused','1','4','9'],['','2','5','10']],settings=dict(columns='3,1'),expected='gameshowell([9,10],[1,2])'),
 dict(id='cohend',rows=[['s1','Pre','10','unused'],['s2','Pre','20',''],['s2','Post','24',''],['s1','Post','13','']],settings=dict(grouping='groups',group='1',value='2',firstGroup='Pre',secondGroup='Post',design='paired',matching='subject',subject='0'),expected='cohend([10,20],[13,24],paired)'),
 dict(id='bayescompare',rows=[['unused','B','4'],['x','A','2'],['y','B','6'],['z','A','3']],settings=dict(grouping='groups',group='1',value='2',firstGroup='A',secondGroup='B'),expected='bayescompare([2,3],[4,6],equal,0,0.01,2,1,0.95,20000,0)'),
 dict(id='kstest',rows=[['unused','B','4'],['x','A','2'],['y','B','6'],['z','A','3']],settings=dict(grouping='groups',group='1',value='2',firstGroup='A',secondGroup='B'),expected='kstest([2,3],[4,6])'),
 dict(id='friedman',rows=[['s1','A','1'],['s2','B','5'],['s1','C','3'],['s2','A','4'],['s1','B','2'],['s2','C','6']],settings=dict(grouping='groups',group='1',value='2',matching='subject',subject='0'),expected='friedman([[1,2,3],[4,5,6]])'),
 dict(id='repeatedanova',rows=[['s1','A','1'],['s2','B','5'],['s1','C','3'],['s2','A','4'],['s1','B','2'],['s2','C','6']],settings=dict(grouping='groups',group='1',value='2',matching='subject',subject='0'),expected='repeatedanova([[1,2,3],[4,5,6]],1)'),
 dict(id='bayesbootstrap',rows=[['s1','Pre','10'],['s2','Pre','20'],['s2','Post','24'],['s1','Post','13']],settings=dict(layout='groups',group='1',value='2',firstGroup='Pre',secondGroup='Post',comparison='paired',matching='subject',subject='0'),expected='bayesbootstrap([10,20],[13,24],mean,0.95,10000,0,paired)'),
 dict(id='mcnemar',rows=[['s1','Pre','yes'],['s2','Pre','no'],['s2','Post','yes'],['s1','Post','yes']],settings=dict(layout='groups',group='1',value='2',firstGroup='Pre',secondGroup='Post',matching='subject',subject='0'),expected='mcnemar([[1,0],[1,0]],exact)'),
 dict(id='twowayanova',rows=[['2','Control','Early','unused'],['4','Control','Late',''],['5','Drug','Early',''],['8','Drug','Late','']],settings=dict(factorA='1',factorB='2',response='0'),expected='twowayanova([[1,1,2],[1,2,4],[2,1,5],[2,2,8]],1)'),
 dict(id='twowayanova',rows=[['2','4','5','8'],['3','6','7','9']],settings=dict(layout='columns'),expected='twowayanova([[1,1,2],[1,1,3],[1,2,4],[1,2,6],[2,1,5],[2,1,7],[2,2,8],[2,2,9]],1)'),
 dict(id='linearmodel',rows=[['2','Control','20','unused'],['4','Drug','30','x']],settings=dict(response='0',predictors='1,2',categorical='1',order='1',ssType='2',coding='treatment'),expected='linearmodel([[1,20,2],[2,30,4]],[1],1,2,treatment)'),
 dict(id='testpower',rows=[],settings=dict(design='paired',effect='0.8',n='24',tail='greater'),expected='testpower(0.8,24,0.05,paired,greater)'),
 dict(id='samplesize',rows=[],settings=dict(design='onesample',power='0.9',tail='less'),expected='samplesize(0.5,0.9,0.05,onesample,less)'),
 dict(id='bootstrapci',rows=[['A','1'],['B','3'],['C','5']],settings=dict(column='1',statistic='median',level='0.9',samples='500',seed='7'),expected='bootstrapci([1,3,5],median,0.9,500,7)'),
 dict(id='cohend',rows=[['A','4','1'],['B','7','2']],settings=dict(first='2',second='1',design='paired'),expected='cohend([1,2],[4,7],paired)'),
 dict(id='eta2',rows=[['B','4'],['A','1'],['B','8'],['A','2']],settings=dict(grouping='groups',group='0',value='1'),expected='eta2([4,8],[1,2])'),
 dict(id='kmeans',rows=[['A','1','9'],['B','2','8']],settings=dict(columns='2,1',clusters='2',seed='7'),expected='kmeans([[9,1],[8,2]],2,7)'),
 dict(id='ordinal',rows=[['2','A','7'],['1','B','4']],settings=dict(response='0',predictors='2'),expected='ordinal([[7,2],[4,1]])'),
 dict(id='multinomial',rows=[['2','A','7'],['1','B','4']],settings=dict(response='0',predictors='2'),expected='multinomial([[7,2],[4,1]])'),
 dict(id='crossvalidate',rows=[['2','A','7'],['1','B','4'],['4','C','8']],settings=dict(response='0',predictors='2',folds='2'),expected='crossvalidate([[7,2],[4,1],[8,4]],2,0)'),
 dict(id='bayesbootstrap',rows=[['1','4'],['2','5'],['','6']],settings=dict(layout='columns',first='0',second='1'),expected='bayesbootstrap([1,2],[4,5,6],mean,0.95,10000,0,independent)'),
 dict(id='bayesbootstrap',rows=[['1','4'],['2','5']],settings=dict(layout='columns',comparison='paired',statistic='median'),expected='bayesbootstrap([1,2],[4,5],median,0.95,10000,0,paired)'),
 dict(id='bayesbootstrap',rows=[['B','4'],['A','1'],['B','5'],['A','2']],settings=dict(layout='groups',order='reverse'),expected='bayesbootstrap([1,2],[4,5],mean,0.95,10000,0,independent)'),
 dict(id='pca',rows=[['A','1','9'],['B','2','8'],['C','3','7']],settings=dict(columns='2,1',components='1',standardize='0'),expected='pca([[9,1],[8,2],[7,3]],1,0)'),
 dict(id='bayesbootstrap',rows=[['A','1'],['B','2'],['C','8']],settings=dict(column='1',statistic='median',level='0.9',samples='500',seed='7'),expected='bayesbootstrap([1,2,8],median,0.9,500,7)'),
 dict(id='bayescompare',rows=[['13','10','unused'],['14','11',''],['15','','']],settings=dict(first='1',second='0',variance='unequal',mu='12',kappa='0.1',alpha='3',beta='4',level='0.9',samples='5000',seed='7'),expected='bayescompare([10,11],[13,14,15],unequal,12,0.1,3,4,0.9,5000,7)'),
 dict(id='ancova',rows=[['B','4','8','unused'],['A','2','5','']],settings=dict(group='0',response='2',predictors='1',slopes='none',level='0.9'),expected='ancova([[1,4,8],[2,2,5]],0.9,0)'),
 dict(id='ancova',rows=[['8','B','4','1'],['5','A','2','3']],settings=dict(group='1',response='0',predictors='3,2'),expected='ancova([[1,1,4,8],[2,3,2,5]],0.95,1)'),
 dict(id='glm',rows=[['2','4','0',''],['3','2','1','']],settings=dict(response='0',predictors='2',family='poisson',adjustment='exposure',offset='1'),expected='glm([[0,2],[1,3]],poisson,auto,1,[4,2],exposure)'),
 dict(id='glm',rows=[['A','1','0'],['B','2','1']],settings=dict(response='2',predictors='1',family='binomial',link='probit'),expected='glm([[1,0],[2,1]],binomial,probit,1)'),
 dict(id='bayesproportion',rows=[['A','1'],['B','0'],['C','1']],settings=dict(column='1',alpha='2',beta='3',level='0.9',threshold='0.6'),expected='bayesproportion([1,0,1],2,3,0.9,0.6)'),
 dict(id='bayesproportion',rows=[['10','7','unused'],['5','2','']],settings=dict(layout='counts',successes='1',trials='0'),expected='bayesproportion([[7,10],[2,5]],1,1,0.95,0.5)'),
 dict(id='bayesrate',rows=[['2','A'],['0','B']],settings=dict(column='0',alpha='2',beta='0.5'),expected='bayesrate([2,0],2,0.5,0.95,1)'),
 dict(id='bayesrate',rows=[['2.5','3',''],['1.5','0','unused']],settings=dict(layout='exposure',column='1',exposure='0',threshold='2'),expected='bayesrate([[3,2.5],[0,1.5]],1,1,0.95,2)'),
 dict(id='bayesmean',rows=[['A','10'],['B','12']],settings=dict(column='1',mu='11',kappa='2',alpha='3',beta='4',level='0.9',threshold='12'),expected='bayesmean([10,12],11,2,3,4,0.9,12)'),
]+[
 dict(id='padjust',rows=[['A','0.01'],['B','0.04'],['C','0.2']],settings=dict(column='1',method=method,alpha='0.1'),expected=f'padjust([0.01,0.04,0.2],{method},0.1)') for method in ('bonferroni','holm','fdr')
]+[
 dict(id=id_,rows=[['B','4'],['A','1'],['B','8'],['A','2']],settings=dict(grouping='groups',group='0',value='1'),expected=f'{id_}([4,8],[1,2])') for id_ in ('levene','bartlett')
]+[
 dict(id='mcnemar',rows=[['No','Yes'],['Yes','Yes'],['No','No'],['Yes','No']],settings=dict(layout='pairs',method='corrected'),expected='mcnemar([[1,1],[1,1]],corrected)'),
 dict(id='kaplanmeier',rows=[['died','9','ignored'],['alive','12','']],settings=dict(time='1',event='0',eventValue='died',level='0.9'),expected='kaplanmeier([[9,1],[12,0]],0.9)'),
 dict(id='logrank',rows=[['A','died','1'],['B','alive','2'],['A','alive','3'],['B','died','4']],settings=dict(time='2',event='1',group='0',eventValue='died'),expected='logrank([[1,1],[3,0]],[[2,0],[4,1]])'),
 dict(id='cox',rows=[['1','9','died',''],['2','12','alive','']],settings=dict(time='1',event='2',eventValue='died',predictors='0'),expected='cox([[9,1,1],[12,0,2]],efron,-1,1)'),
 dict(id='cox',rows=[['1','9','died',''],['2','12','alive','']],settings=dict(time='1',event='2',eventValue='died',predictors='0',ties='breslow',ph='none'),expected='cox([[9,1,1],[12,0,2]],breslow,-1,0)'),
 dict(id='cox',rows=[['1','9','died','5'],['2','12','alive','6']],settings=dict(time='1',event='2',eventValue='died',truncation='entry',entry='0',predictors='3'),expected='cox([[9,1,1,5],[12,0,2,6]],efron,2,1)'),
 dict(id='repeatedanova',rows=[['A','2','4','5'],['B','3','4','7']],settings=dict(columns='1,3'),expected='repeatedanova([[2,5],[3,7]],1)'),
 dict(id='repeatedanova',rows=[['A','2','4','5','7','8','9'],['B','3','4','7','6','9','10']],settings=dict(columns='1,2,3,4,5,6',factor2='3'),expected='repeatedanova([[2,4,5,7,8,9],[3,4,7,6,9,10]],3)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,reml)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='1'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],1,reml)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='poisson'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],poisson,independence)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='binomial',corr='exchangeable'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],binomial,exchangeable)'),
 dict(id='gee',rows=[['A','2','0','1'],['A','4','1','2'],['B','3','0','3']],settings=dict(subject='0',response='3',predictors='1,2',interactions='2,3'),expected='gee([[1,2,0,1],[1,4,1,2],[2,3,0,3]],gaussian,independence,[[1,2]])'),
 dict(id='gee',rows=[['A','2','0','1'],['A','4','1','2'],['B','3','0','3']],settings=dict(subject='0',response='3',predictors='1,2',interactions='p1,p2'),expected='gee([[1,2,0,1],[1,4,1,2],[2,3,0,3]],gaussian,independence,[[1,2]])'),
 dict(id='kstest',rows=[['1','4'],['2',''],['3','5']],settings=dict(first='1',second='0',mode='two'),expected='kstest([4,5],[1,2,3])'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='normal',location='5',scale='2'),expected='kstest([4,5],normal,5,2)'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='uniform',location='3',scale='4'),expected='kstest([4,5],uniform,3,4)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='0',method='reml'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,reml)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',method='ml'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,ml)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='1',method='ml'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],1,ml)'),
 dict(id='mixedmodel',rows=[['2','A','7','0'],['4','A','9','1'],['3','B','5','0'],['5','B','6','1']],settings=dict(subject='1',response='0',predictors='2,3',slope='1,2'),expected='mixedmodel([[1,7,0,2],[1,9,1,4],[2,5,0,3],[2,6,1,5]],[1,2],reml)'),
 dict(id='impute',rows=[['1','NA'],['2','4'],['NA','6'],['4','8']],settings=dict(method='knn',k='3'),expected='impute([[1,NA],[2,4],[NA,6],[4,8]],knn,3)'),
 dict(id='impute',rows=[['1',''],['2','4'],['','6'],['4','8']],settings=dict(method='median'),expected='impute([[1,NA],[2,4],[NA,6],[4,8]],median)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','3'],['2','4'],['3','7']],settings=dict(folds='2',seed='7',split='blocked',model='ridge',alpha='0.25'),expected='crossvalidate([[0,1],[1,3],[2,4],[3,7]],2,7,blocked,ridge,0.25)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','0'],['2','1'],['3','1']],settings=dict(folds='2',seed='0',split='random',model='logistic',alpha='0.5'),expected='crossvalidate([[0,1],[1,0],[2,1],[3,1]],2,0,random,logistic,0.5)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','2'],['2','3'],['3','5']],settings=dict(folds='2',seed='0',split='random',model='elasticnet',alpha='0.2',ratio='0.25'),expected='crossvalidate([[0,1],[1,2],[2,3],[3,5]],2,0,random,elasticnet,[0.2,0.25])')
]
cases += [
 dict(id='glmm',rows=[['0','A','0'],['1','A','1'],['1','B','0']],settings=dict(subject='1',response='0',predictors='2'),expected='glmm([[1,0,0],[1,1,1],[2,0,1]],binomial,15)'),
 dict(id='glmm',rows=[['A','2','0','4'],['A','3','1','2'],['B','4','0','5']],settings=dict(subject='0',response='1',family='poisson',points='21',adjustment='exposure',offset='3'),expected='glmm([[1,0,2],[1,1,3],[2,0,4]],poisson,21,[4,2,5],exposure)'),
 dict(id='glmm',rows=[['0','A','0',''],['1','A','1','']],settings=dict(subject='1',response='0',predictors='2',family='binomial',adjustment='exposure',offset='3'),expected='glmm([[1,0,0],[1,1,1]],binomial,15)'),
 dict(id='poissonreg',rows=[['2','4','0'],['3','2','1'],['4','5','0']],settings=dict(response='0',adjustment='exposure',offset='1'),expected='poissonreg([[0,2],[1,3],[0,4]],[4,2,5],exposure)'),
 dict(id='nbreg',rows=[['2','4','0'],['3','2','1'],['4','5','0']],settings=dict(response='0',adjustment='offset',offset='1'),expected='nbreg([[0,2],[1,3],[0,4]],[4,2,5],offset)')
]
cases += [
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',ci='profile'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,reml,profile)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',ci='bootstrap',ciSamples='300',ciSeed='7'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,reml,[bootstrap,300,7])'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',correction='small'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],gaussian,independence,[],small)'),
 dict(id='glmm',rows=[['0','A','0'],['1','A','1'],['1','B','0']],settings=dict(subject='1',response='0',predictors='2',sensitivity='refit'),expected='glmm([[1,0,0],[1,1,1],[2,0,1]],binomial,15,[],offset,refit)')
]
cases.extend([
 dict(id='sem',rows=[['A','1','2','3','4','5','6'],['B','2','3','4','5','6','7']],settings=dict(columns='0,1,2,3,4,5,6',groupMode='multi',group='0',factors='[1,1,1,2,2,2]'),expected='sem([[1,2,3,4,5,6],[2,3,4,5,6,7]],[1,1,1,2,2,2],[[1,2]],[],complete,[1,2],configural)'),
 dict(id='cfa',rows=[['1','2','3','A','4','5','6'],['2','3','4','B','5','6','7']],settings=dict(columns='0,1,2,3,4,5,6',groupMode='multi',group='3',factors='[1,1,1,2,2,2]'),expected='cfa([[1,2,3,4,5,6],[2,3,4,5,6,7]],[1,1,1,2,2,2],[],complete,[1,2],configural)'),
 dict(id='efa',rows=[['1','2','3'],['4','5','6']],settings=dict(extraction='pa',rotation='none',factors='1'),expected='efa([[1,2,3],[4,5,6]],1,none,pa,0,0,0.95)'),
 dict(id='cfa',rows=[['A','1','2','3'],['B','2','3','4']],settings=dict(columns='1,2,3',factors='1,1,1',groupMode='multi',group='0',invariance='scalar'),expected='cfa([[1,2,3],[2,3,4]],[1,1,1],[],complete,[1,2],scalar)'),
 dict(id='sem',rows=[['A','1','2','3','1','2','3'],['B','2','3','4','2','3','4']],settings=dict(columns='1,2,3,4,5,6',groupMode='multi',group='0',invariance='strict',estimator='wlsmv',missing='fiml'),expected='sem([[1,2,3,1,2,3],[2,3,4,2,3,4]],[1,1,1,2,2,2],[[1,2]],[],complete,[1,2],strict,wlsmv)'),
 dict(id='cfa',rows=[['1','2','3'],['2','3','4']],settings=dict(factors='1,1,1',estimator='wlsmv'),expected='cfa([[1,2,3],[2,3,4]],[1,1,1],[],complete,[],configural,wlsmv)'),
 dict(id='efa',rows=[['1','2','3'],['4','5','6']],settings=dict(extraction='pca',rotation='promax',factors='parallel',seed='7'),expected='efa([[1,2,3],[4,5,6]],parallel,promax,pca,100,7,0.95)'),
 dict(id='cfa',rows=[['A','1','','3','4','5','6'],['B','2','3','4','5','6','7']],settings=dict(columns='1,2,3,4,5,6',factors='1,1,1,2,2,2',cross='2,2',missing='fiml',groupMode='multi',group='0',invariance='metric'),expected='cfa([[1,NA,3,4,5,6],[2,3,4,5,6,7]],[1,1,1,2,2,2],[[2,2]],fiml,[1,2],metric)'),
 dict(id='sem',rows=[['1','2','3','4','5','6']],settings=dict(cross='5,1'),expected='sem([[1,2,3,4,5,6]],[1,1,1,2,2,2],[[1,2]],[[5,1]],complete,[],configural)'),
 dict(id='manova',rows=[['A','lo','1','2'],['B','hi','3','4']],settings=dict(design='factorial',factorColumns='0,1',responses='2,3',order='2'),expected='manova([[1,1,1,2],[2,2,3,4]],factorial,2,2)'),
 dict(id='manova',rows=[['ID','1','2','3'],['','4','5','6']],settings=dict(design='repeated',responses='1,2,3',occasions='3'),expected='manova([[1,2,3],[4,5,6]],repeated,3)'),
 dict(id='glm',rows=[['0','2'],['1','4'],['2','3']],settings=dict(family='nbinom',dispersionMode='estimate'),expected='glm([[0,2],[1,4],[2,3]],nbinom,auto,estimate)'),
 dict(id='glm',rows=[['0','2','4'],['1','4','2'],['2','3','5']],settings=dict(response='1',predictors='0',family='nbinom',dispersionMode='estimate',adjustment='exposure',offset='2'),expected='glm([[0,2],[1,4],[2,3]],nbinom,auto,estimate,[4,2,5],exposure)'),
 dict(id='glmm',rows=[['0','A','0'],['1','A','1'],['1','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='1',sensitivity='refit'),expected='glmm([[1,0,0],[1,1,1],[2,0,1]],binomial,1,[],offset,likelihood,1)'),
 dict(id='glmm',rows=[['A','2','0','4'],['A','3','1','2'],['B','4','0','5']],settings=dict(subject='0',response='1',predictors='2',family='poisson',slope='1',adjustment='exposure',offset='3'),expected='glmm([[1,0,2],[1,1,3],[2,0,4]],poisson,1,[4,2,5],exposure,likelihood,1)')
])
cases.extend([
 dict(id='cronbach',rows=[['ID','1','2'],['','3','4']],settings=dict(columns='2,1',mode='standardized'),expected='cronbach([[2,1],[4,3]],standardized)'),
 dict(id='efa',rows=[['A','1','2','3'],['B','4','5','6']],settings=dict(columns='1,2,3',factors='1',rotation='none'),expected='efa([[1,2,3],[4,5,6]],1,none)'),
 dict(id='cfa',rows=[['id','1','2','3'],['','4','5','6']],settings=dict(columns='1,2,3',factors='1,1,1'),expected='cfa([[1,2,3],[4,5,6]],[1,1,1])'),
 dict(id='sem',rows=[['1','2','3','4','5','6']],settings={},expected='sem([[1,2,3,4,5,6]],[1,1,1,2,2,2],[[1,2]])'),
 dict(id='manova',rows=[['A','1','2',''],['B','4','5','unused']],settings=dict(responses='2,1'),expected='manova([[1,2,1],[2,5,4]])'),
 dict(id='mediation',rows=[['8','1','2','7',''],['9','4','5','6','ignored']],settings=dict(x='1',middle='2',response='0',covariates='3',samples='500',seed='7'),expected='mediation([[1,2,7,8],[4,5,6,9]],500,7)'),
 dict(id='moderation',rows=[['8','1','2',''],['9','4','5','ignored']],settings=dict(x='1',middle='2',response='0'),expected='moderation([[1,2,8],[4,5,9]])'),
 dict(id='cramerv',rows=[['ID','A','X'],['','A','Y'],['id','B','Y']],settings=dict(layout='pairs',first='1',second='2'),expected='cramerv([[1,1],[0,1]])'),
 dict(id='phi',rows=[['A','X'],['A','Y'],['B','Y']],settings=dict(layout='pairs'),expected='phi([[1,1],[0,1]])'),
 dict(id='cohenkappa',rows=[['high','low'],['low','low'],['mid','high']],settings=dict(layout='pairs',weights='linear',categories='low,mid,high'),expected='cohenkappa([[1,0,0],[0,0,1],[1,0,0]],linear)'),
 dict(id='cohenkappa',rows=[['high','low'],['low','low'],['mid','high']],settings=dict(layout='pairs',weights='quadratic',categories='low,mid,high'),expected='cohenkappa([[1,0,0],[0,0,1],[1,0,0]],quadratic)'),
 dict(id='cohenkappa',rows=[['unused','25','4','2'],['','3','20','5'],['id','1','6','24']],settings=dict(columns='1,2,3',weights='quadratic'),expected='cohenkappa([[25,4,2],[3,20,5],[1,6,24]],quadratic)'),
 dict(id='dunn',rows=[['A','1',''],['B','2','x'],['A','3',''],['B','4','']],settings=dict(grouping='groups',group='0',value='1',adjustment='bonferroni'),expected='dunn([[1,3],[2,4]],bonferroni)'),
 dict(id='discriminantanalysis',rows=[['A','1','2',''],['B','4','5','ignored']],settings=dict(response='0',predictors='2,1',method='qda',prior='equal'),expected='discriminantanalysis([[2,1,1],[5,4,2]],qda,equal)'),
 dict(id='quantreg',rows=[['ID','1','8'],['','2','9']],settings=dict(response='2',predictors='1',quantile='0.25'),expected='quantreg([[1,8],[2,9]],0.25)'),
 dict(id='zeroinflated',rows=[['ID','1','8'],['','2','9']],settings=dict(response='2',predictors='1',family='nbinom',inflation='same'),expected='zeroinflated([[1,8],[2,9]],nbinom,same)'),
 dict(id='tobit',rows=[['8','ID','1'],['9','','2']],settings=dict(response='0',predictors='2',lower='none',upper='10'),expected='tobit([[1,8],[2,9]],none,10)'),
 dict(id='hcluster',rows=[['ID','1','2'],['','3','4']],settings=dict(columns='2,1',clusters='1',linkage='average',standardize='0'),expected='hcluster([[2,1],[4,3]],1,average,0)'),
])
(ROOT/'tests/fixtures/statistics_forms.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'app/src/main/assets/advanced_statistics.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for language in ('','_ko'):
    path=ROOT/f'app/src/main/assets/catalog_help{language}.md'
    text=path.read_text(encoding='utf-8').split('\n## Advanced statistics')[0].split('\n## 고급 통계')[0]
    heading='고급 통계' if language else 'Advanced statistics'
    intro=('현재 데이터·예제·직접 입력한 분석 식 중 입력 방식을 선택합니다. 대응 분석은 완전한 쌍을 사용하며 대상 ID로 연결할 수 있습니다. 반복측정은 대상별 완전한 행을 입력하세요. 결측 셀은 impute에서 NA로 처리합니다.' if language else 'Choose current data, example parameters or an editable analysis expression. Paired comparisons use complete pairs and support subject-ID matching. Enter complete subject rows for repeated measurements; impute treats missing cells as NA.')
    text+='\n## '+heading+'\n\n'+intro+'\n\n'
    previous_group = None
    for item in schema:
        group = (item['section'], item['group'])
        if group != previous_group:
            text += '### ' + (item['groupKo'] if language else item['group']) + '\n\n'
            previous_group = group
        text+=f"`{item['id']}` — {USES[item['id']][int(bool(language))]} {item['helpKo'] if language else item['help']}\nExample: {item['example']}\n\n"
    text=enrich_help(text,bool(language))
    path.write_text(text.rstrip()+'\n',encoding='utf-8')
