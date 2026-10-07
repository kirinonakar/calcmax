"""Author shared advanced-analysis presets and bilingual catalog help."""
import json
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GROUPS = '[1,2,4,5],[2,3,5,8]'
COUNTS = '[[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]]'
CATEGORIES = '[[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]]'
CLUSTERS = '[[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]]'
SURVIVAL = '[[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]]'
specs = [
    ('padjust','Multiple testing','다중검정 보정','list',',holm,0.05','[0.01,0.04,0.03,0.2]', 'p values; method bonferroni / holm / fdr (BH) / by; alpha.', 'p값 목록; 방법 bonferroni / holm / fdr (BH) / by; 유의수준.'),
    ('cohend',"Cohen’s d","Cohen의 d",'groups',',independent',GROUPS,'Two samples; independent (pooled d) or paired (dz).','두 표본; independent(합동 SD) 또는 paired(차이의 SD).'),
    ('eta2','η² effect size','η² 효과크기','groups','',GROUPS,'Independent groups as separate lists.','독립 그룹별 목록.'),
    ('levene','Levene / Brown–Forsythe','Levene / Brown–Forsythe','groups','',GROUPS,'Separate group lists; median-centered equal-variance test.','그룹별 목록; 중앙값 기준 등분산 검정.'),
    ('bartlett','Bartlett','Bartlett','groups','',GROUPS,'Separate group lists; normality assumption.','그룹별 목록; 정규성 가정.'),
    ('mcnemar','McNemar','McNemar','table',',exact','[[20,8],[2,15]]','Paired 2×2 count table; exact / corrected / asymptotic.','대응 2×2 빈도표; exact / corrected / asymptotic.'),
    ('bayesproportion','Bayesian proportion','베이지안 비율','list',',1,1,0.95,0.5','[1,1,0,1,0,1,1,1,0,1]','Binary 0/1 list or [[successes,trials],...]; Beta prior alpha, beta (default 1,1); credible level; threshold p0 in (0,1). Returns equal-tailed interval, P(p>p0), next-success probability and BF10 (Beta alternative / point null p=p0).','0/1 목록 또는 [[성공 수,시행 수],...]; Beta 사전 alpha,beta(기본 1,1), 구간 수준, 기준 p0(0~1 사이). 등꼬리 구간·P(p>p0)·다음 성공 확률·BF10(Beta 대립 / p=p0 점귀무).'),
    ('bayesmean','Bayesian mean','베이지안 평균','list',',0,1,2,1,0.95,0','[1,2,3,4,5]','Normal sample, unknown variance; prior mu0,kappa0,alpha0,beta0; credible level; threshold. Variance ~ InvGamma(alpha0,beta0), mean | variance ~ Normal(mu0,variance/kappa0). Defaults 0,1,2,1 are proper, scale-dependent priors. Returns Student-t mean interval and next-observation predictive interval.','분산 미지의 정규 표본; 사전 mu0,kappa0,alpha0,beta0, 구간 수준, 기준값. 분산 ~ InvGamma(alpha0,beta0), 평균|분산 ~ Normal(mu0,분산/kappa0). 기본 0,1,2,1은 자료 척도에 맞춰 조절할 적정 사전분포. 평균의 t 구간과 다음 관측 예측구간.'),
    ('bayesrate','Bayesian Poisson rate','베이지안 발생률','list',',1,1,0.95,1','[0,2,1,3,2]','Count list (one exposure unit each) or [[count,exposure],...]; Gamma prior shape, rate (inverse scale, default 1,1); credible level; nonnegative threshold. Equal-tailed rate interval and predictive count mean/SD for one exposure unit.','횟수 목록(관측당 노출 1) 또는 [[횟수,노출량],...]; Gamma 사전 shape,rate(척도의 역수, 기본 1,1), 구간 수준, 0 이상 기준값. 발생률 등꼬리 구간과 노출 1단위의 예측 횟수 평균·SD.'),
    ('kaplanmeier','Kaplan–Meier','Kaplan–Meier','table',',0.95',SURVIVAL,'Rows: time, event (1=event, 0=censored); confidence level.','열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.'),
    ('logrank','Log-rank','로그순위 검정','survivalgroups','','[[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]]','Two time/event tables. Current data: time, event, group (exactly two groups).','두 시간/사건 표. 현재 데이터 열: 시간, 사건, 그룹(2개).'),
    ('survivalanalysis','Survival analysis','생존분석','table',',0,efron,-1,1','[[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]]','Rows: time, event (0/1), group ID, optional Cox predictors; Cox 0=off, 1=on; then ties and the PH check.','열: 시간, 사건(0/1), 그룹 ID, 선택적 Cox 설명변수. Cox 0=끔, 1=켬; 이어서 동률 처리와 PH 검정.'),
    ('cox','Cox regression','Cox 회귀','table',',efron,-1,1','[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]]','Rows: time, event 0/1, predictors. Ties efron (default) or breslow; entry column for left truncation (-1 none); PH check 0/1.','열: 시간, 사건 0/1, 설명변수. 동률 efron(기본)/breslow, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.'),
    ('repeatedanova','Repeated-measures ANOVA','반복측정 ANOVA','table',',1','[[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]]','Rows=subjects, columns=conditions. Second-factor levels: 1 = one-way, 2+ = two-way (first factor slowest); GG corrections.','행=대상, 열=조건. 둘째 요인 수준: 1=일요인, 2 이상=이요인(첫 요인 최외곽); GG 보정.'),
    ('mixedmodel','Mixed model','혼합모형','table',',0',CLUSTERS,'Rows: subject ID, predictors, response. Gaussian random intercept with up to three random slopes (0 none, a predictor position, or [1,2]); third argument ml (default) or reml; up to 5000 rows.','열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기(0 없음, 변수 위치, 또는 [1,2]); 셋째 인수 ml(기본)·reml; 최대 5000행.'),
    ('gee','GEE','GEE','table',',gaussian,independence',CLUSTERS,'Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independent / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE.','열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE.'),
    ('multinomial','Multinomial logistic','다항 로지스틱','table','',CATEGORIES,'Rows: predictors, numeric category response. Smallest category is reference.','열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준.'),
    ('ordinal','Ordinal logistic','순서형 로지스틱','table','',CATEGORIES,'Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.','열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓.'),
    ('poissonreg','Poisson regression','포아송 회귀','table','',COUNTS,'Rows: predictors, integer count response. Log link.','열: 설명변수, 정수 빈도 반응. 로그 연결함수.'),
    ('nbreg','Negative binomial regression','음이항 회귀','table','','[[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]]','Rows: predictors, integer count response. NB2 with estimated dispersion.','열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정.'),
    ('bootstrapci','Bootstrap confidence interval','부트스트랩 신뢰구간','list',',mean,0.95,2000,0','[1,2,3,4,5,8]','Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.','통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식.'),
    ('testpower','Power','검정력','none','', '0.5,64,0.05,independent','Cohen d, n per group/pairs, alpha, independent / paired / onesample, alternative two (default) / greater / less. Exact noncentral-t power.','Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample, 대립가설 two(기본) / greater / less. 정확 noncentral-t 검정력.'),
    ('samplesize','Sample size','표본수','none','','0.5,0.8,0.05,independent','Cohen d, target power, alpha, design, alternative. Exact noncentral-t power.','Cohen d, 목표 검정력, 유의수준, 설계, 대립가설. 정확 noncentral-t 검정력.'),
    ('kstest','Kolmogorov–Smirnov','Kolmogorov–Smirnov','groups','',GROUPS,'Two sample lists, or kstest(data,normal,mu,sigma) / kstest(data,uniform,lower,width). Continuous null; one-sample p is asymptotic.','두 표본 목록 또는 kstest(data,normal,평균,SD) / kstest(data,uniform,하한,폭). 연속분포 가정; 일표본 p는 근사.'),
    ('crossvalidate','Cross-validation','교차검증','table',',3,0','[[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]]','Rows: predictors, response; folds, seed; split random (default) / blocked / stratified; model linear (default) / ridge / lasso / elasticnet / logistic; penalty alpha or [alpha,l1 ratio].','열: 설명변수, 반응; 폴드 수, 시드; 분할 random(기본) / blocked / stratified; 모형 linear(기본) / ridge / lasso / elasticnet / logistic; 벌점 alpha 또는 [alpha,l1 비율].'),
    ('pca','PCA','주성분 분석','table',',2,1','[[1,2],[2,1],[3,4],[4,3],[5,7]]','Rows=observations, columns=features; components, standardize 1/0.','행=관측, 열=변수; 주성분 수, 표준화 1/0.'),
    ('kmeans','K-means clustering','K-means 군집','table',',2,0','[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]]','Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.','숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도.'),
    ('impute','Missing-value imputation','결측치 대체','table',',mean','[[1,NA],[2,4],[NA,6],[4,8]]','NA for missing cells; mean / median / mode / regression / knn with neighbours (default 5). Single imputation.','결측값은 NA; mean / median / mode / regression / knn(이웃 수 기본 5). 단일 대체.'),
]
schema = [{'id':id_,'label':label,'ko':ko,'input':layout,'suffix':suffix,'example':f'{id_}({data}{suffix})','help':help_,'helpKo':helpko} for id_,label,ko,layout,suffix,data,help_,helpko in specs]
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
forms={
    'bayesproportion':[field('layout','Data','자료 형태','choice','binary',[('binary','Binary observations (0/1)','0/1 관측값'),('counts','Successes / trials','성공 수·시행 수')]),
        dict(col('column','Observation column','관측값 열',0),when={'layout':['binary']}),
        dict(col('successes','Successes','성공 수 열',0),when={'layout':['counts']}),dict(col('trials','Trials','시행 수 열',1),when={'layout':['counts']})]+bayesian_prior+credible_fields+[field('threshold','Threshold p0','기준 비율 p0','number','0.5')],
    'bayesmean':[col('column','Sample column','표본 열',0),field('mu','Prior mean μ0','사전 평균 μ0','number','0'),field('kappa','Prior strength κ0','사전 강도 κ0','number','1'),field('alpha','Variance prior α0','분산 사전 α0','number','2'),field('beta','Variance prior β0','분산 사전 β0','number','1')]+credible_fields+[field('threshold','Threshold mean','기준 평균','number','0')],
    'bayesrate':[field('layout','Data','자료 형태','choice','counts',[('counts','Counts (exposure = 1)','횟수 (노출량 = 1)'),('exposure','Counts / exposure','횟수·노출량')]),
        col('column','Count column','횟수 열',0),dict(col('exposure','Exposure','노출량 열',1),when={'layout':['exposure']}),field('alpha','Prior shape α','사전 shape α','number','1'),field('beta','Prior rate β','사전 rate β','number','1')]+credible_fields+[field('threshold','Threshold rate','기준 발생률','number','1')],
    'padjust':[col('column','p-value column','p값 열',0),field('method','Correction','보정 방법','choice','holm',[('bonferroni','Bonferroni','Bonferroni'),('holm','Holm','Holm'),('fdr','FDR (BH)','FDR (BH)')]),field('alpha','Significance α','유의수준 α','number','0.05')],
    'levene':group_fields,'bartlett':group_fields,
    'mcnemar':[field('layout','Data','자료 형태','choice','counts',[('counts','2×2 counts','2×2 빈도표'),('pairs','Paired observations','대응 관측값')]),col('first','Before / first','이전·첫째 열',0),col('second','After / second','이후·둘째 열',1),field('method','Method','검정 방법','choice','exact',[('exact','Exact','정확 검정'),('corrected','Continuity corrected','연속성 보정'),('asymptotic','Asymptotic','점근 검정')])],
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
    'mixedmodel':cluster_fields+[field('slope','Random-slope predictors','랜덤 기울기 변수','number','0'),field('method','Estimation','추정 방법','choice','ml',[('ml','ML','ML'),('reml','REML','REML')])],
    'gee':cluster_fields+[field('family','Family','분포','choice','gaussian',[('gaussian','Gaussian','Gaussian'),('binomial','Binomial (0/1)','이항 (0/1)'),('poisson','Poisson','포아송')]),field('corr','Working correlation','작업상관','choice','independence',[('independence','Independent','독립'),('exchangeable','Exchangeable','교환가능'),('ar1','AR(1)','AR(1)')]),field('interactions','Interactions (columns or names)','상호작용 (열·이름)','number','')],
    'kstest':[field('mode','Samples / distribution','표본·분포','choice','two',[('two','Two samples','두 표본'),('normal','Normal','정규분포'),('uniform','Uniform','균등분포')]),col('first','Sample column','표본 열',0),dict(col('second','Second sample','둘째 표본 열',1),when={'mode':['two']}),dict(field('location','Mean / lower bound','평균·하한','number','0'),when={'mode':['normal','uniform']}),dict(field('scale','SD / width','표준편차·폭','number','1'),when={'mode':['normal','uniform']})],
    'impute':[field('method','Method','대체 방법','choice','mean',[('mean','Mean','평균'),('median','Median','중앙값'),('mode','Mode','최빈값'),('regression','Regression','회귀'),('knn','k-NN','k-NN')]),dict(field('k','Neighbours','이웃 수','number','5'),when={'method':['knn']})],
    'crossvalidate':[field('folds','Folds','폴드 수','number','3'),field('seed','Seed','시드','number','0'),
        field('split','Split','분할','choice','random',[('random','Random','무작위'),('blocked','Blocked','블록'),('stratified','Stratified','층화')]),
        field('model','Model','모형','choice','linear',[('linear','Linear (OLS)','선형 (OLS)'),('ridge','Ridge','Ridge'),('lasso','Lasso','Lasso'),('elasticnet','Elastic net','Elastic net'),('logistic','Logistic (0/1)','로지스틱 (0/1)')]),
        dict(field('alpha','Penalty α','벌점 α','number','0.1'),when={'model':['ridge','lasso','elasticnet','logistic']}),
        dict(field('ratio','L1 ratio','L1 비율','number','0.5'),when={'model':['elasticnet']})]
}
form_help={
 'bayesproportion':('Beta prior → posterior proportion · credible interval · P(p > p0). BF10: Beta alternative / point null p=p0.','Beta 사전 → 사후 비율 · 베이지안 구간 · P(p > p0). BF10: Beta 대립 / p=p0 점귀무.'),
 'bayesmean':('Normal data, unknown variance. Adjust the normal-inverse-gamma prior to your data scale; mean interval and next-observation prediction.','분산 미지의 정규 자료. 자료 척도에 맞춰 정규-역감마 사전을 조절하세요. 평균 구간·다음 관측 예측.'),
 'bayesrate':('Gamma prior → Poisson rate · credible interval · P(rate > threshold). β is rate, not scale.','Gamma 사전 → 포아송 발생률 · 베이지안 구간 · 기준 초과 확률. β는 rate(척도의 역수)입니다.'),
 'padjust':('Adjust p values from the selected column.','선택한 열의 p값을 보정합니다.'),
 'levene':('Compare group variances using median centers.','중앙값 기준으로 그룹의 분산을 비교합니다.'),
 'bartlett':('Compare variances of normally distributed groups.','정규분포를 가정해 그룹의 분산을 비교합니다.'),
 'mcnemar':('Use two paired category columns or a 2×2 count table.','두 대응 범주 열 또는 2×2 빈도표를 사용합니다.'),
 'kaplanmeier':('Choose time and event columns; other event values are censored.','시간·사건 열을 선택합니다. 발생 값 이외는 중도절단입니다.'),
 'logrank':('Compare exactly two groups; other event values are censored.','두 그룹을 비교합니다. 발생 값 이외는 중도절단입니다.'),
 'survivalanalysis':('Kaplan–Meier curves · log-rank · Cox; other event values are censored.','Kaplan–Meier 곡선 · log-rank · Cox. 발생 값 이외는 중도절단입니다.'),
 'cox':('Proportional hazards; Breslow/Efron ties, optional entry column for left truncation and a scaled-Schoenfeld PH check.','비례위험; Breslow/Efron 동률, 선택적 진입시간 열(좌측 절단), 스케일된 Schoenfeld PH 검정.'),
 'repeatedanova':('One row per subject; one or two within factors with GG corrections.','행마다 한 대상. 일·이요인 반복측정·GG 보정입니다.'),
 'mixedmodel':('Gaussian random intercept with up to three random slopes under ML or REML. Random-slope positions accept 0, a number or a list such as 1,2.','Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기, ML·REML 추정입니다. 랜덤 기울기 위치는 0, 번호, 또는 1,2 같은 목록입니다.'),
 'gee':('Working correlation independent / exchangeable / AR(1); cluster-robust SE. Interactions accept header names (age,weight), the shown column letters or labels (y,z / age (y),weight (z)), column numbers (2,3) or predictor order (p1,p2); separate pairs with ;.','작업상관 independent / exchangeable / AR(1); 군집 강건 표준오차. 상호작용은 열 이름(age,weight), 표시된 열 문자·라벨(y,z / age (y),weight (z)), 열 번호(2,3), 설명변수 순서(p1,p2)로 입력하고 쌍은 ;로 구분합니다.'),
 'kstest':('Compare two samples or a specified continuous distribution.','두 표본 또는 지정한 연속분포와 비교합니다.'),
 'impute':('Fill missing NA cells by mean, median, mode, regression or k-NN.','결측값(NA)을 평균·중앙값·최빈값·회귀·k-NN으로 대체합니다.'),
 'crossvalidate':('Held-out folds fitted on training rows only; choose split, model and penalty.','훈련 행으로만 적합하는 홀드아웃 폴드; 분할·모형·벌점을 선택합니다.')
}
def literal(node):
    if isinstance(node,ast.Name) and node.id=='NA': return 'NA'
    if isinstance(node,(ast.List,ast.Tuple)): return [literal(element) for element in node.elts]
    return ast.literal_eval(node)


for item in schema:
    if item['id'] not in forms: continue
    item['controls']=forms[item['id']]
    item['formHelp'],item['formHelpKo']=form_help[item['id']]
    arguments=ast.parse(item['example'],mode='eval').body.args
    first=literal(arguments[0])
    if item['id'] in ('padjust','bayesproportion','bayesmean','bayesrate'): rows=[[v] for v in first]
    elif item['id'] in ('levene','bartlett','kstest'):
        samples=[literal(arg) for arg in arguments]; rows=[[sample[i] if i<len(sample) else '' for sample in samples] for i in range(max(map(len,samples)))]
    elif item['id']=='logrank': rows=[r+[i+1] for i,arg in enumerate(arguments) for r in literal(arg)]
    else: rows=first
    item['exampleRows']=[[str(v) for v in row] for row in rows]
(ROOT/'tests/fixtures').mkdir(exist_ok=True)
cases=[
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
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='1'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],1)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='poisson'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],poisson,independence)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='binomial',corr='exchangeable'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],binomial,exchangeable)'),
 dict(id='gee',rows=[['A','2','0','1'],['A','4','1','2'],['B','3','0','3']],settings=dict(subject='0',response='3',predictors='1,2',interactions='2,3'),expected='gee([[1,2,0,1],[1,4,1,2],[2,3,0,3]],gaussian,independence,[[1,2]])'),
 dict(id='gee',rows=[['A','2','0','1'],['A','4','1','2'],['B','3','0','3']],settings=dict(subject='0',response='3',predictors='1,2',interactions='p1,p2'),expected='gee([[1,2,0,1],[1,4,1,2],[2,3,0,3]],gaussian,independence,[[1,2]])'),
 dict(id='kstest',rows=[['1','4'],['2',''],['3','5']],settings=dict(first='1',second='0',mode='two'),expected='kstest([4,5],[1,2,3])'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='normal',location='5',scale='2'),expected='kstest([4,5],normal,5,2)'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='uniform',location='3',scale='4'),expected='kstest([4,5],uniform,3,4)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='0',method='reml'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0,reml)'),
 dict(id='mixedmodel',rows=[['2','A','7','0'],['4','A','9','1'],['3','B','5','0'],['5','B','6','1']],settings=dict(subject='1',response='0',predictors='2,3',slope='1,2'),expected='mixedmodel([[1,7,0,2],[1,9,1,4],[2,5,0,3],[2,6,1,5]],[1,2])'),
 dict(id='impute',rows=[['1','NA'],['2','4'],['NA','6'],['4','8']],settings=dict(method='knn',k='3'),expected='impute([[1,NA],[2,4],[NA,6],[4,8]],knn,3)'),
 dict(id='impute',rows=[['1',''],['2','4'],['','6'],['4','8']],settings=dict(method='median'),expected='impute([[1,NA],[2,4],[NA,6],[4,8]],median)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','3'],['2','4'],['3','7']],settings=dict(folds='2',seed='7',split='blocked',model='ridge',alpha='0.25'),expected='crossvalidate([[0,1],[1,3],[2,4],[3,7]],2,7,blocked,ridge,0.25)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','0'],['2','1'],['3','1']],settings=dict(folds='2',seed='0',split='random',model='logistic',alpha='0.5'),expected='crossvalidate([[0,1],[1,0],[2,1],[3,1]],2,0,random,logistic,0.5)'),
 dict(id='crossvalidate',rows=[['0','1'],['1','2'],['2','3'],['3','5']],settings=dict(folds='2',seed='0',split='random',model='elasticnet',alpha='0.2',ratio='0.25'),expected='crossvalidate([[0,1],[1,2],[2,3],[3,5]],2,0,random,elasticnet,[0.2,0.25])')
]
(ROOT/'tests/fixtures/statistics_forms.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'app/src/main/assets/advanced_statistics.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for language in ('','_ko'):
    path=ROOT/f'app/src/main/assets/catalog_help{language}.md'
    text=path.read_text(encoding='utf-8').split('\n## Advanced statistics')[0].split('\n## 고급 통계')[0]
    heading='고급 통계' if language else 'Advanced statistics'
    intro=('통계 화면의 고급 분석에서 보정 방법, 자료 열, 그룹, 설명변수, 검정 옵션을 직접 선택합니다. 현재 데이터·예제·분석 식을 전환할 수 있습니다. 표 분석은 선택한 열의 빈 셀을 자동 삭제하지 않습니다. impute는 빈 셀을 NA로 변환합니다. 모든 고급 분석은 64비트 수치 계산입니다.' if language else 'In Statistics, Advanced analysis provides controls for correction methods, column roles, groups, predictors and test options. Switch between current data, examples and an editable expression. Table analyses reject blank selected cells; impute converts them to NA. All advanced analyses use binary64 numerics.')
    text+='\n## '+heading+'\n\n'+intro+'\n\n'
    for item in schema:
        text+=f"`{item['id']}` — {item['helpKo'] if language else item['help']}\nExample: {item['example']}\n\n"
    text+=('베이지안 분석은 독립 관측과 지정한 우도·적정 공액 사전분포를 사용하며 구간은 등꼬리 사후확률 구간입니다. Bayes factor는 가설의 사후확률이 아니며 사전분포에 영향을 받습니다. 계산 근거: ' if language else 'Bayesian analyses assume independent observations and the stated likelihood with proper conjugate priors; intervals are equal-tailed posterior credible intervals. A Bayes factor is not a posterior hypothesis probability and depends on the prior. References: ')
    text+='[Stanford conjugate priors](https://web.stanford.edu/class/stats200/Lecture21.pdf), [normal-inverse-gamma analysis](https://treese41528.github.io/ComputationalDataScience/Website/part3_bayesian/chapter5/ch5_2-prior-distributions.html).\n\n'
    text+=('모형은 수렴하지 않거나 식별 불가능하면 오류를 반환합니다. Cox는 Breslow/Efron 동률, 선택적 좌측 절단, Grambsch–Therneau 스케일된 Schoenfeld 비례위험 검정을 지원하며 순서형 로지스틱은 비례오즈를 가정합니다. 혼합모형은 랜덤 절편과 최대 세 개의 랜덤 기울기(ML·REML)를, GEE는 독립·교환가능·AR(1) 작업상관을 지원합니다. 반복측정 ANOVA는 GG 보정이 포함된 균형 일·이요인 설계를 다룹니다. 단일 대체(mean·median·mode·회귀·k-NN) 후 추론은 대체 불확실성을 반영하지 않습니다. 교차검증은 linear·ridge·lasso·elasticnet·logistic 모형과 random·blocked·stratified 분할을 지원합니다. Firth 추론은 프로파일 페널티 우도 신뢰구간을, 부트스트랩은 백분위 구간을 사용합니다(BCa 없음).\n' if language else 'Models return errors on failed convergence or non-identifiability. Cox supports Breslow/Efron ties, optional left truncation and a Grambsch–Therneau scaled-Schoenfeld proportional-hazards check; ordinal logistic assumes proportional odds. Mixed models support a random intercept plus up to three random slopes under ML or REML; GEE supports independent, exchangeable and AR(1) working correlations. Repeated-measures ANOVA covers balanced one- and two-way within-subject designs with GG corrections. Single imputation (mean, median, mode, regression or k-NN) does not propagate imputation uncertainty. Cross-validation covers linear, ridge, lasso, elastic-net and logistic fits with random, blocked or stratified splits. Firth inference uses profile penalized-likelihood intervals; bootstrap CIs use the percentile method, not BCa.\n')
    path.write_text(text,encoding='utf-8')
