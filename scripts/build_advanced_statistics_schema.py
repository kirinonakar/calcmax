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
    ('kaplanmeier','Kaplan–Meier','Kaplan–Meier','table',',0.95',SURVIVAL,'Rows: time, event (1=event, 0=censored); confidence level.','열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.'),
    ('logrank','Log-rank','로그순위 검정','survivalgroups','','[[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]]','Two time/event tables. Current data: time, event, group (exactly two groups).','두 시간/사건 표. 현재 데이터 열: 시간, 사건, 그룹(2개).'),
    ('survivalanalysis','Survival analysis','생존분석','table',',0,breslow,-1,1','[[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]]','Rows: time, event (0/1), group ID, optional Cox predictors; Cox 0=off, 1=on; then ties and the PH check.','열: 시간, 사건(0/1), 그룹 ID, 선택적 Cox 설명변수. Cox 0=끔, 1=켬; 이어서 동률 처리와 PH 검정.'),
    ('cox','Cox regression','Cox 회귀','table',',breslow,-1,1','[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]]','Rows: time, event 0/1, predictors. Ties breslow/efron; entry column for left truncation (-1 none); PH check 0/1.','열: 시간, 사건 0/1, 설명변수. 동률 breslow/efron, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.'),
    ('repeatedanova','Repeated-measures ANOVA','반복측정 ANOVA','table',',1','[[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]]','Rows=subjects, columns=conditions. Second-factor levels: 1 = one-way, 2+ = two-way (first factor slowest); GG corrections.','행=대상, 열=조건. 둘째 요인 수준: 1=일요인, 2 이상=이요인(첫 요인 최외곽); GG 보정.'),
    ('mixedmodel','Mixed model','혼합모형','table',',0',CLUSTERS,'Rows: subject ID, predictors, response. Gaussian random intercept ML; second argument selects a random slope (0 none); ≤300 rows.','열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 ML; 둘째 인수는 랜덤 기울기 위치(0 없음); 최대 300행.'),
    ('gee','GEE','GEE','table',',gaussian,independence',CLUSTERS,'Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independent / exchangeable / ar1; sandwich SE.','열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 강건 SE.'),
    ('multinomial','Multinomial logistic','다항 로지스틱','table','',CATEGORIES,'Rows: predictors, numeric category response. Smallest category is reference.','열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준.'),
    ('ordinal','Ordinal logistic','순서형 로지스틱','table','',CATEGORIES,'Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.','열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓.'),
    ('poissonreg','Poisson regression','포아송 회귀','table','',COUNTS,'Rows: predictors, integer count response. Log link.','열: 설명변수, 정수 빈도 반응. 로그 연결함수.'),
    ('nbreg','Negative binomial regression','음이항 회귀','table','','[[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]]','Rows: predictors, integer count response. NB2 with estimated dispersion.','열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정.'),
    ('bootstrapci','Bootstrap confidence interval','부트스트랩 신뢰구간','list',',mean,0.95,2000,0','[1,2,3,4,5,8]','Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.','통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식.'),
    ('testpower','Power','검정력','none','', '0.5,64,0.05,independent','Cohen d, n per group/pairs, alpha, independent / paired / onesample. Two-sided normal approximation.','Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample. 양측 정규근사.'),
    ('samplesize','Sample size','표본수','none','','0.5,0.8,0.05,independent','Cohen d, target power, alpha, design. Two-sided normal approximation.','Cohen d, 목표 검정력, 유의수준, 설계. 양측 정규근사.'),
    ('kstest','Kolmogorov–Smirnov','Kolmogorov–Smirnov','groups','',GROUPS,'Two sample lists, or kstest(data,normal,mu,sigma) / kstest(data,uniform,lower,width). Continuous null; one-sample p is asymptotic.','두 표본 목록 또는 kstest(data,normal,평균,SD) / kstest(data,uniform,하한,폭). 연속분포 가정; 일표본 p는 근사.'),
    ('crossvalidate','Cross-validation','교차검증','table',',3,0','[[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]]','Rows: predictors, response; folds, seed. Shuffled k-fold OLS.','열: 설명변수, 반응; 폴드 수, 시드. 무작위 k-fold OLS.'),
    ('pca','PCA','주성분 분석','table',',2,1','[[1,2],[2,1],[3,4],[4,3],[5,7]]','Rows=observations, columns=features; components, standardize 1/0.','행=관측, 열=변수; 주성분 수, 표준화 1/0.'),
    ('kmeans','K-means clustering','K-means 군집','table',',2,0','[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]]','Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.','숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도.'),
    ('impute','Missing-value imputation','결측치 대체','table',',mean','[[1,NA],[2,4],[NA,6],[4,8]]','NA for missing cells; mean / median / mode. Single imputation.','결측값은 NA; mean / median / mode. 단일 대체.'),
]
schema = [{'id':id_,'label':label,'ko':ko,'input':layout,'suffix':suffix,'example':f'{id_}({data}{suffix})','help':help_,'helpKo':helpko} for id_,label,ko,layout,suffix,data,help_,helpko in specs]
def field(key,label,ko,type_,default,choices=None,when=None):
    result=dict(key=key,label=label,ko=ko,type=type_,default=default)
    if choices: result['choices']=[dict(id=id_,label=en,ko=ko_) for id_,en,ko_ in choices]
    if when: result['when']=when
    return result
def col(key,en,ko,default): return field(key,en,ko,'column',default)
def multi(key,en,ko): return field(key,en,ko,'columns','auto')
grouping=field('grouping','Grouping','그룹 구성','choice','columns',[('columns','Columns','열별 그룹'),('groups','Group / value columns','그룹·값 열')])
group_fields=[grouping,dict(multi('columns','Group columns','그룹 열'),when={'grouping':['columns']}),dict(col('group','Group column','그룹 열',0),when={'grouping':['groups']}),dict(col('value','Value column','값 열',1),when={'grouping':['groups']})]
survival_fields=[col('time','Time','시간 열',0),col('event','Event','사건 열',1),field('eventValue','Event value','사건 발생 값','number','1')]
cluster_fields=[col('subject','Subject / cluster','대상·군집 열',0),col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열')]
forms={
    'padjust':[col('column','p-value column','p값 열',0),field('method','Correction','보정 방법','choice','holm',[('bonferroni','Bonferroni','Bonferroni'),('holm','Holm','Holm'),('fdr','FDR (BH)','FDR (BH)')]),field('alpha','Significance α','유의수준 α','number','0.05')],
    'levene':group_fields,'bartlett':group_fields,
    'mcnemar':[field('layout','Data','자료 형태','choice','counts',[('counts','2×2 counts','2×2 빈도표'),('pairs','Paired observations','대응 관측값')]),col('first','Before / first','이전·첫째 열',0),col('second','After / second','이후·둘째 열',1),field('method','Method','검정 방법','choice','exact',[('exact','Exact','정확 검정'),('corrected','Continuity corrected','연속성 보정'),('asymptotic','Asymptotic','점근 검정')])],
    'kaplanmeier':survival_fields+[field('level','Confidence level','신뢰수준','number','0.95')],
    'logrank':survival_fields+[col('group','Group','그룹 열',2)],
    'survivalanalysis':survival_fields+[field('grouping','Groups','그룹','choice','groups',[('groups','Group column','그룹 열'),('all','All subjects','전체 대상')]),
        dict(col('group','Group column','그룹 열',2),when={'grouping':['groups']}),
        field('cox','Cox model','Cox 모형','choice','0',[('0','Off','끔'),('1','On','켬')]),
        dict(multi('predictors','Cox predictors','Cox 설명변수 열'),when={'cox':['1']}),
        field('ties','Tie handling','동률 처리','choice','breslow',[('breslow','Breslow','Breslow'),('efron','Efron','Efron')]),
        dict(field('ph','Proportional-hazards check','비례위험 검정','choice','test',[('test','Time-rank test','시간순위 검정'),('none','Skip','생략')]),when={'cox':['1']})],
    'cox':survival_fields+[multi('predictors','Predictors','설명변수 열'),
        field('ties','Tie handling','동률 처리','choice','breslow',[('breslow','Breslow','Breslow'),('efron','Efron','Efron')]),
        field('truncation','Left truncation','좌측 절단','choice','none',[('none','None','없음'),('entry','Entry-time column','진입시간 열')]),
        dict(col('entry','Entry time','진입시간 열',2),when={'truncation':['entry']}),
        field('ph','Proportional-hazards check','비례위험 검정','choice','test',[('test','Time-rank test','시간순위 검정'),('none','Skip','생략')])],
    'repeatedanova':[multi('columns','Condition columns','조건 열'),field('factor2','Second-factor levels','둘째 요인 수준','number','1')],
    'mixedmodel':cluster_fields+[field('slope','Random-slope predictor','랜덤 기울기 변수','number','0')],
    'gee':cluster_fields+[field('family','Family','분포','choice','gaussian',[('gaussian','Gaussian','Gaussian'),('binomial','Binomial (0/1)','이항 (0/1)'),('poisson','Poisson','포아송')]),field('corr','Working correlation','작업상관','choice','independence',[('independence','Independent','독립'),('exchangeable','Exchangeable','교환가능'),('ar1','AR(1)','AR(1)')])],
    'kstest':[field('mode','Samples / distribution','표본·분포','choice','two',[('two','Two samples','두 표본'),('normal','Normal','정규분포'),('uniform','Uniform','균등분포')]),col('first','Sample column','표본 열',0),dict(col('second','Second sample','둘째 표본 열',1),when={'mode':['two']}),dict(field('location','Mean / lower bound','평균·하한','number','0'),when={'mode':['normal','uniform']}),dict(field('scale','SD / width','표준편차·폭','number','1'),when={'mode':['normal','uniform']})]
}
form_help={
 'padjust':('Adjust p values from the selected column.','선택한 열의 p값을 보정합니다.'),
 'levene':('Compare group variances using median centers.','중앙값 기준으로 그룹의 분산을 비교합니다.'),
 'bartlett':('Compare variances of normally distributed groups.','정규분포를 가정해 그룹의 분산을 비교합니다.'),
 'mcnemar':('Use two paired category columns or a 2×2 count table.','두 대응 범주 열 또는 2×2 빈도표를 사용합니다.'),
 'kaplanmeier':('Choose time and event columns; other event values are censored.','시간·사건 열을 선택합니다. 발생 값 이외는 중도절단입니다.'),
 'logrank':('Compare exactly two groups; other event values are censored.','두 그룹을 비교합니다. 발생 값 이외는 중도절단입니다.'),
 'survivalanalysis':('Kaplan–Meier curves · log-rank · Cox; other event values are censored.','Kaplan–Meier 곡선 · log-rank · Cox. 발생 값 이외는 중도절단입니다.'),
 'cox':('Proportional hazards; Breslow/Efron ties, optional entry column for left truncation and a time-rank PH check.','비례위험; Breslow/Efron 동률, 선택적 진입시간 열(좌측 절단), 시간순위 PH 검정.'),
 'repeatedanova':('One row per subject; one or two within factors with GG corrections.','행마다 한 대상. 일·이요인 반복측정·GG 보정입니다.'),
 'mixedmodel':('Gaussian random intercept with an optional random slope (ML).','Gaussian 랜덤 절편과 선택적 랜덤 기울기 (ML)입니다.'),
 'gee':('Working correlation independent / exchangeable / AR(1); cluster-robust SE.','작업상관 independent / exchangeable / AR(1); 군집 강건 표준오차입니다.'),
 'kstest':('Compare two samples or a specified continuous distribution.','두 표본 또는 지정한 연속분포와 비교합니다.')
}
for item in schema:
    if item['id'] not in forms: continue
    item['controls']=forms[item['id']]
    item['formHelp'],item['formHelpKo']=form_help[item['id']]
    arguments=ast.parse(item['example'],mode='eval').body.args
    first=ast.literal_eval(arguments[0])
    if item['id']=='padjust': rows=[[v] for v in first]
    elif item['id'] in ('levene','bartlett','kstest'):
        samples=[ast.literal_eval(arg) for arg in arguments]; rows=[[sample[i] if i<len(sample) else '' for sample in samples] for i in range(max(map(len,samples)))]
    elif item['id']=='logrank': rows=[r+[i+1] for i,arg in enumerate(arguments) for r in ast.literal_eval(arg)]
    else: rows=first
    item['exampleRows']=[[str(v) for v in row] for row in rows]
(ROOT/'tests/fixtures').mkdir(exist_ok=True)
cases=[
 dict(id='padjust',rows=[['A','0.01'],['B','0.04'],['C','0.2']],settings=dict(column='1',method=method,alpha='0.1'),expected=f'padjust([0.01,0.04,0.2],{method},0.1)') for method in ('bonferroni','holm','fdr')
]+[
 dict(id=id_,rows=[['B','4'],['A','1'],['B','8'],['A','2']],settings=dict(grouping='groups',group='0',value='1'),expected=f'{id_}([4,8],[1,2])') for id_ in ('levene','bartlett')
]+[
 dict(id='mcnemar',rows=[['No','Yes'],['Yes','Yes'],['No','No'],['Yes','No']],settings=dict(layout='pairs',method='corrected'),expected='mcnemar([[1,1],[1,1]],corrected)'),
 dict(id='kaplanmeier',rows=[['died','9','ignored'],['alive','12','']],settings=dict(time='1',event='0',eventValue='died',level='0.9'),expected='kaplanmeier([[9,1],[12,0]],0.9)'),
 dict(id='logrank',rows=[['A','died','1'],['B','alive','2'],['A','alive','3'],['B','died','4']],settings=dict(time='2',event='1',group='0',eventValue='died'),expected='logrank([[1,1],[3,0]],[[2,0],[4,1]])'),
 dict(id='cox',rows=[['1','9','died',''],['2','12','alive','']],settings=dict(time='1',event='2',eventValue='died',predictors='0'),expected='cox([[9,1,1],[12,0,2]],breslow,-1,1)'),
 dict(id='cox',rows=[['1','9','died',''],['2','12','alive','']],settings=dict(time='1',event='2',eventValue='died',predictors='0',ties='efron',ph='none'),expected='cox([[9,1,1],[12,0,2]],efron,-1,0)'),
 dict(id='cox',rows=[['1','9','died','5'],['2','12','alive','6']],settings=dict(time='1',event='2',eventValue='died',truncation='entry',entry='0',predictors='3'),expected='cox([[9,1,1,5],[12,0,2,6]],breslow,2,1)'),
 dict(id='repeatedanova',rows=[['A','2','4','5'],['B','3','4','7']],settings=dict(columns='1,3'),expected='repeatedanova([[2,5],[3,7]],1)'),
 dict(id='repeatedanova',rows=[['A','2','4','5','7','8','9'],['B','3','4','7','6','9','10']],settings=dict(columns='1,2,3,4,5,6',factor2='3'),expected='repeatedanova([[2,4,5,7,8,9],[3,4,7,6,9,10]],3)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],0)'),
 dict(id='mixedmodel',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',slope='1'),expected='mixedmodel([[1,0,2],[1,1,4],[2,0,3]],1)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='poisson'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],poisson,independence)'),
 dict(id='gee',rows=[['2','A','0'],['4','A','1'],['3','B','0']],settings=dict(subject='1',response='0',predictors='2',family='binomial',corr='exchangeable'),expected='gee([[1,0,2],[1,1,4],[2,0,3]],binomial,exchangeable)'),
 dict(id='kstest',rows=[['1','4'],['2',''],['3','5']],settings=dict(first='1',second='0',mode='two'),expected='kstest([4,5],[1,2,3])'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='normal',location='5',scale='2'),expected='kstest([4,5],normal,5,2)'),
 dict(id='kstest',rows=[['1','4'],['2','5']],settings=dict(first='1',mode='uniform',location='3',scale='4'),expected='kstest([4,5],uniform,3,4)')
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
    text+=('모형은 수렴하지 않거나 식별 불가능하면 오류를 반환합니다. Cox는 Breslow/Efron 동률, 선택적 좌측 절단, 시간순위 비례위험 검정을 지원하며 순서형 로지스틱은 비례오즈를 가정합니다. 혼합모형은 랜덤 절편과 최대 하나의 랜덤 기울기를, GEE는 독립·교환가능·AR(1) 작업상관을 지원합니다. 반복측정 ANOVA는 GG 보정이 포함된 균형 일·이요인 설계를 다룹니다. 단일 대체 후 추론은 대체 불확실성을 반영하지 않습니다. 교차검증은 OLS 분할과 규제 α 선택을 지원하며 시계열·군집 분할은 포함하지 않습니다. Firth 추론은 프로파일 페널티 우도 신뢰구간을, 부트스트랩은 백분위 구간을 사용합니다(BCa 없음).\n' if language else 'Models return errors on failed convergence or non-identifiability. Cox supports Breslow/Efron ties, optional left truncation and an approximate time-rank proportional-hazards check; ordinal logistic assumes proportional odds. Mixed models support a random intercept plus at most one random slope; GEE supports independent, exchangeable and AR(1) working correlations. Repeated-measures ANOVA covers balanced one- and two-way within-subject designs with GG corrections. Single imputation does not propagate imputation uncertainty. Cross-validation covers OLS splits and regularized alpha selection, without grouped or time-series splits. Firth inference uses profile penalized-likelihood intervals; bootstrap CIs use the percentile method, not BCa.\n')
    path.write_text(text,encoding='utf-8')
