"""Bilingual test-selection guidance shared by the catalog-help generator."""
import re

GUIDES = [
'''What do you want to compare?
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
''',
'''무엇을 비교하나요?
├─ 숫자 자료
│  ├─ 한 표본 평균 vs 기준값 → 단일 표본 t 검정
│  ├─ 독립된 두 그룹 → Welch t 검정
│  ├─ 같은 대상의 전·후 → 대응 t 검정
│  ├─ 독립된 3개 이상 그룹 → Welch ANOVA → Games–Howell
│  │  └─ 등분산 ANOVA 선택 → Tukey–Kramer
│  ├─ 같은 대상의 여러 조건 → 반복측정 ANOVA / Friedman
│  ├─ 독립 관측의 두 요인 → 이요인 ANOVA
│  ├─ 3개 이상 요인·숫자 공변량 → 요인 선형회귀
│  └─ 공변량을 보정한 그룹 비교 → ANCOVA
├─ 범주·빈도 자료
│  ├─ 독립된 범주 간 관계 → χ² 독립성 검정
│  │  └─ 기대빈도가 작은 2×2 표 → Fisher 정확 검정
│  ├─ 대응된 이항 결과 → McNemar
│  └─ 빈도 vs 기대빈도 → χ² 적합도 검정
├─ 중도절단이 있는 사건 발생 시간 → 생존분석
└─ 변수로 반응값을 예측 → 회귀·모형

분포·이상값·연구 설계도 확인하세요:
  평균 분석 → Shapiro–Wilk + Q–Q plot
  등분산 가정 → Brown–Forsythe / Levene
  독립 표본의 순위 비교 → Mann–Whitney (2개), Kruskal–Wallis (3개 이상)
  대칭적인 대응 차이값 → Wilcoxon 부호순위
''']

USES = {
 'linearmodel':('Fit numeric and categorical predictors with automatic interactions and Type II/III joint term F tests; supports one, two, three or more factors.','숫자·범주 설명변수·자동 상호작용을 적합하고 Type II/III 항별 부분 F 검정을 제공합니다. 1·2·3개 이상 요인을 지원합니다.'),
 'twowayanova':('Compare independent observations across two factors, testing both main effects and their interaction.','독립 관측에서 두 요인의 주효과와 상호작용을 함께 비교합니다.'),
 'friedman':('Compare three or more matched conditions by within-subject ranks; chi-square approximation with tie correction.','대상 안의 순위로 3개 이상 대응 조건을 비교하며 동점 보정 χ² 근사를 사용합니다.'),
 'welchanova':('Compare independent group means with unequal variances; automatically includes Games–Howell comparisons.','이분산 독립 그룹 평균을 비교하며 Games–Howell 사후비교를 자동 제공합니다.'),
 'gameshowell':('Compare each pair of independent group means without equal variances, with adjusted p values and simultaneous intervals.','등분산을 가정하지 않고 독립 그룹의 모든 쌍을 보정 p값·동시 구간으로 비교합니다.'),
 'ttest':('Compare one sample mean with a target, such as average score against 70.','한 표본 평균을 기준값과 비교할 때 사용합니다. 예: 평균 점수가 70인지 비교.'),
 'ttest2':('Compare means of two unrelated groups; Welch allows unequal variances.','서로 다른 두 그룹의 평균을 비교합니다. Welch 방식은 이분산도 허용합니다.'),
 'ttestpaired':('Compare before/after measurements or matched pairs; analyze A−B differences.','같은 대상의 전·후 또는 짝지은 표본을 비교하며 A−B 차이값을 분석합니다.'),
 'ztest':('Compare a mean with a target only when population SD is known.','모집단 표준편차를 알고 있을 때 평균을 기준값과 비교합니다.'),
 'ztest2':('Compare two independent means when both population SDs are known.','두 모집단 표준편차가 알려진 독립 두 그룹의 평균을 비교합니다.'),
 'anova':('Compare means across independent groups; review normal errors and equal variances.','독립 그룹들의 평균을 비교합니다. 오차의 정규성·등분산을 확인합니다.'),
 'tukey':('Identify which group means differ after ANOVA, with familywise multiplicity correction.','ANOVA 이후 어느 그룹 평균이 다른지 다중비교 보정과 함께 확인합니다.'),
 'shapiro':('Check evidence against normality; interpret with Q–Q plots, not as a pass/fail gate.','정규성에 반하는 근거를 점검합니다. 통과·실패 판정이 아니라 Q–Q plot과 함께 해석합니다.'),
 'chi2test':('Compare observed category frequencies with specified expected frequencies.','관측 범주 빈도가 지정한 기대빈도와 맞는지 비교합니다.'),
 'chi2independence':('Test association between two independent categorical variables.','독립된 관측에서 두 범주형 변수의 연관성을 검정합니다.'),
 'fisherexact':('Test association in a 2×2 table, especially with small expected counts.','2×2 표의 연관성을 검정하며 기대빈도가 작을 때 특히 적절합니다.'),
 'wilcoxon':('Compare paired differences using ranks when a symmetric location-shift model is appropriate.','대응 차이값의 대칭적인 위치 차이 모형이 적절할 때 순위로 비교합니다.'),
 'mannwhitney':('Compare distributions of two independent groups using ranks; a median interpretation needs similar shapes.','독립 두 그룹의 분포를 순위로 비교합니다. 중앙값 차이 해석에는 비슷한 분포 형태가 필요합니다.'),
 'kruskal':('Compare distributions of independent groups using ranks; normality is not required.','독립 그룹의 분포를 순위로 비교하며 정규성은 필요하지 않습니다.'),
 'tinterval':('Estimate a mean with uncertainty when population SD is unknown.','모집단 표준편차가 미지일 때 평균과 불확실성을 추정합니다.'),
 'zinterval':('Estimate a mean interval when population SD is known.','모집단 표준편차가 알려진 평균의 구간을 추정합니다.'),
 'padjust':('Correct a family of p values when several hypotheses are tested together.','여러 가설을 함께 검정할 때 한 묶음의 p값을 보정합니다.'),
 'cohend':('Describe the standardized mean difference between two independent or paired samples.','독립·대응 두 표본 평균 차이의 표준화된 크기를 설명합니다.'),
 'eta2':('Describe the proportion of total variation associated with group differences.','전체 변동 중 그룹 차이와 연관된 비율을 설명합니다.'),
 'levene':('Check equality of group variances; median-centered Brown–Forsythe is less sensitive to non-normality.','그룹의 등분산을 점검합니다. 중앙값 기준 Brown–Forsythe는 비정규성에 덜 민감합니다.'),
 'bartlett':('Check equal variances when group distributions are reasonably normal.','그룹 분포가 대체로 정규일 때 등분산을 점검합니다.'),
 'mcnemar':('Compare paired binary outcomes, such as yes/no before and after.','동일 대상의 전·후 예/아니오 같은 대응 이항 결과를 비교합니다.'),
 'ancova':('Compare group means while adjusting for numeric covariates.','숫자 공변량을 보정하면서 그룹 평균을 비교합니다.'),
 'repeatedanova':('Compare repeated conditions within the same subjects in a balanced design.','균형 설계에서 같은 대상의 반복 조건을 비교합니다.'),
 'glm':('Model a response using a family and link suited to its distribution.','반응변수 분포에 맞는 분포족·연결함수로 모형을 적합합니다.'),
 'poissonreg':('Model event counts, optionally accounting for exposure.','필요하면 노출량을 보정하여 사건 횟수를 모형화합니다.'),
 'nbreg':('Model counts with extra variation beyond a Poisson model.','포아송보다 변동이 큰 과산포 빈도를 모형화합니다.'),
 'multinomial':('Predict unordered numeric categories from predictors.','설명변수로 순서 없는 숫자 범주를 예측합니다.'),
 'ordinal':('Predict ordered categories under a proportional-odds model.','비례오즈 모형으로 순서가 있는 범주를 예측합니다.'),
 'mixedmodel':('Model continuous responses with repeated subjects or clusters and random effects.','반복 대상·군집과 랜덤효과를 포함해 연속 반응을 모형화합니다.'),
 'glmm':('Model clustered binary or count outcomes with subject-specific random effects.','대상별 랜덤효과를 포함해 군집 이항·빈도 반응을 모형화합니다.'),
 'gee':('Estimate population-average effects for repeated or clustered outcomes.','반복·군집 반응의 모집단 평균 효과를 추정합니다.'),
 'survivalanalysis':('Analyze censored time-to-event data as a Kaplan–Meier, log-rank and optional Cox set.','중도절단이 있는 사건 시간을 Kaplan–Meier·로그순위·선택적 Cox 세트로 분석합니다.'),
 'kaplanmeier':('Estimate survival over time while accounting for censoring.','중도절단을 반영하여 시간에 따른 생존확률을 추정합니다.'),
 'logrank':('Compare survival between two groups without adjusting for predictors.','설명변수 보정 없이 두 그룹의 생존을 비교합니다.'),
 'cox':('Relate predictors to event hazard; review proportional hazards.','설명변수와 사건 위험의 관계를 분석하며 비례위험을 점검합니다.'),
 'bootstrapci':('Estimate an IID statistic interval by resampling observed values.','관측값의 IID 재표집으로 통계량의 구간을 추정합니다.'),
 'bayesbootstrap':('Quantify posterior uncertainty in statistics using random weights on observations.','관측값의 무작위 가중치로 통계량의 사후 불확실성을 추정합니다.'),
 'bayesmean':('Estimate a normal mean with a chosen prior and predictive interval.','지정한 사전분포로 정규 평균과 예측구간을 추정합니다.'),
 'bayescompare':('Compare two independent normal means with posterior differences and Bayes factors.','사후 차이·Bayes factor로 독립 정규 두 표본 평균을 비교합니다.'),
 'bayesproportion':('Estimate a binary success proportion using a Beta prior.','Beta 사전분포로 이항 성공 비율을 추정합니다.'),
 'bayesrate':('Estimate a Poisson event rate using counts and exposure.','빈도·노출량으로 포아송 사건 발생률을 추정합니다.'),
 'kstest':('Compare continuous distributions or a sample with a fully specified distribution.','연속분포끼리 또는 표본과 모수가 지정된 연속분포를 비교합니다.'),
 'testpower':('Evaluate power for a planned t-test design and effect size.','계획한 t 검정 설계·효과크기의 검정력을 평가합니다.'),
 'samplesize':('Plan the sample size needed for a target t-test power.','목표 t 검정력을 위한 표본수를 계획합니다.'),
 'crossvalidate':('Assess held-out predictive performance rather than training fit.','훈련 적합도가 아닌 홀드아웃 예측 성능을 평가합니다.'),
 'pca':('Summarize correlated numeric features using fewer components.','상관된 숫자 변수들을 더 적은 주성분으로 요약합니다.'),
 'kmeans':('Group observations by numeric-feature similarity; check feature scales first.','숫자 변수의 유사성으로 관측을 군집화하며 먼저 변수 척도를 확인합니다.'),
 'impute':('Prepare incomplete data by single imputation; subsequent inference omits imputation uncertainty.','단일 대체로 불완전한 자료를 준비합니다. 이후 추론은 대체 불확실성을 반영하지 않습니다.'),
}

ASSUMPTION_GUIDES = [
'''### Parametric methods with normal-model assumptions
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
''',
'''### 정규 모형 가정이 필요한 모수 방법 (parametric)
- 단일·대응 t 검정, t 신뢰구간: 정확한 소표본 추론은 모집단의 정규성을 가정합니다. 대응 검정에서는 원자료가 아닌 차이값의 정규성입니다. 큰 표본에서는 어느 정도 강건할 수 있지만 왜도·영향이 큰 이상값을 함께 확인하세요.
- Welch t: 정규 모형에 근거한 평균 비교이며 등분산은 필요하지 않습니다. Student 합동분산 t는 등분산도 필요합니다. 기본값은 Welch이며 Student도 선택할 수 있습니다.
- Welch ANOVA·Games–Howell: 정규 모형의 독립 평균 비교이며 등분산은 필요하지 않습니다. 일요인 분석의 기본 세트입니다.
- 이요인 ANOVA·요인 선형회귀: 정규·독립 오차, 잔차 등분산, 식별 가능한 반복 관측 설계와 적절한 상호작용이 필요합니다. Type II는 주변성 원리를, Type III 요인 효과는 합 대비를 사용합니다.
- 일반 ANOVA·Tukey: 독립된 정규 오차와 그룹 간 등분산을 가정합니다. ANCOVA는 공변량 효과의 선형성·공통 기울기, 반복측정 ANOVA는 대상 내 구조·구형성 및 보정을 함께 확인합니다.
- Gaussian 혼합모형: 조건부 오차·랜덤효과의 정규성을 가정하며 전체 원자료가 정규여야 한다는 뜻은 아닙니다. 베이지안 평균·두 표본 비교도 지정한 정규 우도를 가정합니다.
- Bartlett: 비정규성에 민감한 분산 검정입니다. 정규성이 의심되면 중앙값 기준 Levene·Brown–Forsythe를 우선 검토하세요.

### 모수 방법이라고 모두 원자료의 정규성이 필요한 것은 아닙니다
- z 검정·z 구간: 알려진 모집단 표준편차와 평균의 정규 또는 적절히 근사된 표집분포가 필요합니다. 표본 표준편차만으로는 조건을 충족하지 못합니다.
- 이항 로지스틱·다항·순서형 모형은 범주 우도, 포아송·음이항 모형은 빈도 우도를 사용합니다. 반응값의 정규성 대신 분포족·연결함수·과산포·연구 설계·모형 진단을 확인하세요.
- GEE는 군집의 평균·분산 모형과 작업상관을 사용하며 원자료의 정규성이 필수는 아닙니다. 강건 공분산도 극소수 군집이나 잘못된 평균 모형을 해결하지 못합니다.
- GLMM은 선택한 분포족·랜덤효과의 가정을 확인합니다. 베이지안 비율·발생률은 각각 이항·포아송 우도를 사용합니다.

### 정규성 가정이 필요 없는 비모수 방법 (nonparametric)
- Mann–Whitney U: 독립된 두 분포. Kruskal–Wallis: 여러 독립 분포. 중앙값·위치 차이 해석에는 비슷한 분포 형태가 필요하며 단순히 평균 검정을 대체하는 방법은 아닙니다.
- Friedman: 독립 대상의 3개 이상 반복 조건을 대상 안에서 순위로 비교합니다. 완전 대응 자료·동점 보정 χ² 근사를 사용합니다.
- Wilcoxon 부호순위: 대응 또는 일표본 차이값을 분석합니다. 위치 차이 해석에는 대칭성이 필요합니다. 차이값의 심한 비대칭은 순위 검정을 선택한다고 해결되지 않습니다.
- Kolmogorov–Smirnov: 연속분포를 비교합니다. 일표본 기준분포는 독립적으로 모수가 지정되어야 합니다. 같은 표본에서 모수를 추정하면 통상 p값이 맞지 않으며 이 앱은 Lilliefors 보정을 제공하지 않습니다.
- Kaplan–Meier·로그순위: 정규성 없이 중도절단 사건 시간을 다루지만 중도절단·연구 설계 가정은 여전히 중요합니다. Cox는 반모수 방법이며 정규성 대신 비례위험을 가정합니다.
- 부트스트랩: 정규성은 필요하지 않지만 이 앱의 IID 재표집에는 독립적이고 대표성 있는 관측이 필요합니다. 대응·군집·시계열에는 자료 구조에 맞는 추론이 필요합니다.

### 범주형 검정: 정규성으로 선택하지 않습니다
- χ² 독립성·적합도: 독립된 빈도와 충분한 기대빈도. Fisher 정확: 희소한 독립 2×2 표. McNemar: 대응 이항 결과. Shapiro p값 대신 연구 설계·빈도를 보고 선택하세요.

### 어느 것을 선택하나요?
- 먼저 질문을 정하세요: 평균 차이, 분포 차이, 연관성, 예측, 생존 중 무엇인가요? 이어서 독립 그룹·대응 관측·군집을 구분하세요.
- 평균이 목적이면 오차 모형·설계가 적절한 t·ANOVA 계열을 사용합니다. Q–Q plot·표본수·왜도·이상값을 함께 확인하고, 독립 두 그룹이 이분산이면 Welch를 사용하세요.
- 순서·순위나 분포 비교가 목적이면 Mann–Whitney·Kruskal–Wallis를, 대칭적인 대응 위치 차이면 Wilcoxon을 검토하세요. 평균에서 분포·위치로 분석 목적이 바뀌었음을 명시하세요.
- 심한 비정규성·비대칭·의존성이 있으면 변환, 적절한 분포·군집 모형, 설계를 반영하는 추론을 검토하세요. 비모수라는 이름만으로 문제가 해결되지는 않습니다.
- Shapiro p ≥ 0.05는 정규성의 증명이 아니며 p < 0.05도 모든 평균 분석의 자동 탈락 기준은 아닙니다. 예비 검정의 유의 여부만으로 주 검정을 몰래 바꾸지 마세요.
''']

COMPARISON_TABLES = [
'''### Independent and paired samples at a glance

| Data structure | Parametric comparison | Nonparametric comparison | Available in this app |
| --- | --- | --- | --- |
| Two independent groups | Student / Welch t-test | Mann–Whitney U | All provided; Welch default |
| Two paired groups | Paired t-test | Wilcoxon signed-rank | Both provided |
| 3+ independent groups | ANOVA / Welch ANOVA | Kruskal–Wallis | All provided; Welch ANOVA default |
| 3+ repeated conditions | Repeated-measures ANOVA | Friedman test | Both provided |

The table compares design-matched families, not interchangeable estimands. Rank comparisons need their own shape/symmetry assumptions for location interpretations. Repeated conditions are measurements of the same subjects, not independent groups.
''',
'''### 독립표본·대응표본에 따른 검정 비교

| 데이터 구조 | 모수 검정 | 비모수 검정 | 이 앱의 지원 범위 |
| --- | --- | --- | --- |
| 독립된 두 집단 | Student / Welch t-test | Mann–Whitney U | 모두 지원; Welch 기본 |
| 대응된 두 집단 | Paired t-test | Wilcoxon signed-rank | 모두 지원 |
| 독립된 3집단 이상 | ANOVA / Welch ANOVA | Kruskal–Wallis | 모두 지원; Welch ANOVA 기본 |
| 반복측정 3조건 이상 | Repeated-measures ANOVA | Friedman test | 모두 지원 |

이 표는 같은 자료 설계에 맞는 방법군을 비교하며 분석 목적까지 서로 같다는 뜻은 아닙니다. 순위 검정을 위치 차이로 해석하려면 형태·대칭성 가정을 별도로 확인하세요. 반복 조건은 같은 대상의 측정이며 독립 그룹이 아닙니다.
''']

def enrich_help(text, korean=False):
    index=int(bool(korean))
    # Keep the choosing guide before the first descriptive-statistics entry.
    # Remove its earlier generated placement before rebuilding it below.
    for old_heading in ('## Stats — choosing a test','## 통계 — 검정 선택'):
        old_start=text.find(old_heading)
        old_end=text.find('\n## ',old_start+3) if old_start>=0 else -1
        if old_end>=0 and text[old_end+1:].startswith('## Data & units'):
            text=text[:old_start]+text[old_end+1:]
    start=text.find('## Statistical tests')
    if start<0: start=text.find('## Stats — choosing a test')
    if start<0: start=text.find('## 통계 — 검정 선택')
    if start>=0:
        first=text.index('`ttest(',start)
        heading='통계 — 검정 선택' if korean else 'Stats — choosing a test'
        caution=('목적·측정척도·연구 설계로 먼저 선택하고, 정규성 p값만으로 검정 방법을 자동 전환하지 마세요. Welch t는 등분산이 필수는 아닙니다. One-way ANOVA의 기본값은 Welch이며 Games–Howell을 함께 실행합니다. 등분산 ANOVA를 선택하면 Tukey를 함께 실행합니다. 순위 검정도 모든 가정에서 자유로운 검정은 아닙니다. 알려진 모집단 SD일 때만 z 검정을 선택하세요.' if korean else 'Choose by purpose, measurement scale and study design first. Do not automatically switch tests based only on a normality p value. Welch t does not require equal variances. One-way ANOVA defaults to Welch with Games–Howell. Choosing equal-variance ANOVA automatically adds Tukey. Rank tests also have assumptions. Use z tests only for known population SDs.')
        suite=('원자료 t 검정·t 구간·ANOVA·Tukey는 표본 요약, 가정 점검, Q–Q plot·분포를 함께 제공합니다. t 검정은 효과크기·95% 양측 평균 구간을, ANOVA는 η²·자동 사후비교를, Tukey/Games–Howell은 전체 ANOVA를 함께 표시합니다. 대응 t는 차이값의 정규성을, ANCOVA·요인 ANOVA·요인 선형회귀는 잔차를 점검합니다. 요약 통계만 입력한 경우 정규성은 확인할 수 없다고 표시합니다.' if korean else 'Raw-data t tests, t intervals, ANOVA and Tukey include sample summaries, assumption checks, Q–Q plots and distributions. t tests add effect sizes and two-sided 95% mean intervals; ANOVA adds η² and automatic post-hoc comparisons; Tukey/Games–Howell add overall ANOVA. Paired t checks differences; ANCOVA and factorial linear models check residuals. Summary-only input explicitly reports that normality cannot be checked.')
        text=text[:start]+'## '+heading+'\n\n```text\n'+GUIDES[index].rstrip()+'\n```\n\n'+COMPARISON_TABLES[index]+'\n'+caution+'\n\n'+suite+'\n\n'+ASSUMPTION_GUIDES[index]+'\n'+text[first:]
    if '`welchanova(' not in text:
        pos=text.find('`tukey(')
        if pos>=0:
            basic=('`ttest2(delta, A, B, student)` — 독립 두 그룹의 합동분산 Student t. 기본 3인수는 Welch입니다.\n\n`welchanova(A, B, ...)` — 이분산 일요인 ANOVA + Games–Howell 자동 세트.\n\n`gameshowell(A, B, ...)` — 이분산 쌍별 사후비교·95% 동시 구간.\n\n' if korean else '`ttest2(delta, A, B, student)` — Pooled Student t; the three-argument default remains Welch.\n\n`welchanova(A, B, ...)` — Unequal-variance one-way ANOVA with automatic Games–Howell.\n\n`gameshowell(A, B, ...)` — Pairwise unequal-variance comparisons and simultaneous 95% intervals.\n\n')
            text=text[:pos]+basic+text[pos:]
    guide_start=text.find('## '+('통계 — 검정 선택' if korean else 'Stats — choosing a test'))
    data_start=text.find('## Data & units')
    if 0<=data_start<guide_start:
        entries=text.index('`ttest(',guide_start)
        guide=text[guide_start:entries]
        text=text[:guide_start]+'## Statistical tests\n\n'+text[entries:]
        text=text[:data_start]+guide+text[data_start:]
    def entry(match):
        signature,description=match.group(1),match.group(2)
        name=signature.split('(')[0]
        if name not in USES: return match.group(0)
        usage=USES[name][index]
        if usage not in description: description=usage+' '+description
        return '`'+signature+'` — '+description
    return re.sub(r'^`([^`]+)`\s*[—–-]\s*(.*)$',entry,text,flags=re.MULTILINE)
