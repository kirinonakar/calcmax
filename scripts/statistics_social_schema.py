"""Shared social-science analysis definitions used by the schema generator."""
import json
import random

rng=random.Random(341)
survey=[]; social=[]
for i in range(48):
    first=rng.gauss(0,1); second=.5*first+rng.gauss(0,1)
    survey.append([round(3+b*first+rng.gauss(0,.55),3) for b in (1,.8,1.2)]+[round(3+b*second+rng.gauss(0,.55),3) for b in (1,.9,1.1)])
    x=rng.gauss(0,1); m=.7*x+rng.gauss(0,.6); y=.3*x+.8*m+.25*x*m+rng.gauss(0,.7)
    social.append([round(v,3) for v in (x,m,y)])
def data(rows): return json.dumps(rows,separators=(',',':'))
REG='[[0,2],[1,4],[2,3],[3,8],[4,7],[5,9],[6,10],[7,12],[8,11],[9,15],[10,17],[11,16]]'
COUNTS=data([[x,y] for x,counts in enumerate(((0,0,0,1,2,3),(0,0,1,2,3,5),(0,0,1,3,5,8),(0,0,2,4,7,10))) for y in counts])
SPECS=[
 ('cronbach','Cronbach α reliability','Cronbach α 신뢰도','table',',raw',data(survey[:24]),'Rows are subjects, columns are items; raw or standardized alpha. Reverse-code items first. Returns corrected item-total correlations and alpha if deleted. Alpha does not establish validity or unidimensionality.','행은 대상, 열은 문항; raw·standardized α. 역문항은 먼저 역코딩합니다. 수정 문항-총점 상관·문항 삭제 시 α를 제공합니다. α만으로 타당성·단일차원성을 입증할 수 없습니다.'),
 ('efa','Exploratory factor analysis (EFA)','탐색적 요인분석 (EFA)','table',',2,varimax',data(survey),'Principal-axis factoring on Pearson correlations, SMC initialization; factor count, varimax or none. KMO, Bartlett sphericity, communalities, loadings and regression scores. Complete numeric rows; no polychoric or oblique rotation.','Pearson 상관의 주축요인법·SMC 초기값; 요인 수·varimax 또는 none. KMO·Bartlett 구형성·공통성·적재량·회귀 요인점수. 완전한 숫자 행; 다분상관·사각회전 미지원.'),
 ('cfa','Confirmatory factor analysis (CFA)','확인적 요인분석 (CFA)','table',',[1,1,1,2,2,2]',data(survey),'Covariance ML for continuous indicators. Specify factor IDs in selected-indicator order; one loading per indicator, at least three indicators per factor, first loading fixed at 1. Correlated factors, independent errors; χ²/df/p, CFI, TLI, RMSEA and SRMR. No ordinal/robust estimator, FIML, cross-loadings or correlated errors.','연속형 지표의 공분산 ML. 선택 지표 순서의 요인 ID; 지표당 한 요인·요인당 최소 3개 지표·첫 적재량 1 고정. 요인 간 상관·독립 오차; χ²/df/p·CFI·TLI·RMSEA·SRMR. 순서형·강건 추정·FIML·교차적재·상관오차 미지원.'),
 ('sem','Structural equation model (SEM)','구조방정식 모형 (SEM)','table',',[1,1,1,2,2,2],[[1,2]]',data(survey),'Continuous covariance ML with the CFA measurement structure and recursive latent paths [source,target]. Exogenous factors may covary; endogenous disturbances are independent. Minimum three indicators per factor. No cycles, ordinal estimator, FIML, equality constraints or multi-group invariance tests.','CFA 측정 구조와 잠재변수 [출발,도착] 경로의 연속형 공분산 ML. 외생 요인 상관·독립 내생 교란. 요인당 최소 3개 지표. 순환·순서형 추정·FIML·동일성 제약·다집단 불변성 검정 미지원.'),
 ('manova','MANOVA','MANOVA (다변량 분산분석)','table','',data([[i//8+1,round(r[0],3),round(r[3],3)] for i,r in enumerate(survey[:24])]),'One-factor independent MANOVA: group ID followed by multiple responses. Pillai F, Wilks Rao F, Hotelling–Lawley and Roy statistics. Assumes multivariate normal errors and equal covariance; no repeated or factorial MANOVA.','독립 일요인 MANOVA: 그룹 ID 뒤에 여러 종속변수. Pillai F·Wilks Rao F·Hotelling–Lawley·Roy 통계량. 다변량 정규 오차·등공분산 가정; 반복·다요인 MANOVA 미지원.'),
 ('mediation','Mediation analysis','매개 분석','table',',2000,0',data(social[:24]),'Rows: X, M, optional covariates, Y. Single continuous mediator; adjusted OLS direct, total and indirect a×b effects, seeded row-bootstrap percentile CI and Sobel approximation. No causal identification from observational association alone.','열: X·M·선택적 공변량·Y. 단일 연속형 매개변수; 조정 OLS 직접·총·간접 a×b 효과, 시드 기반 행 부트스트랩 백분위 CI·Sobel 근사. 관측 연관성만으로 인과관계를 식별하지 않습니다.'),
 ('moderation','Moderation analysis','조절 분석','table','',data(social[:24]),'Rows: X, W, optional covariates, Y. Centered X/W and X×W interaction; conditional slopes at W mean ± SD with full covariance t inference. Continuous moderator, independent OLS errors.','열: X·W·선택적 공변량·Y. 중심화 X/W·X×W 상호작용; W 평균±SD의 조건부 기울기·전체 공분산 t 추론. 연속형 조절변수·독립 OLS 오차.'),
 ('cramerv','Cramér’s V','Cramér의 V','table','','[[20,5],[7,18]]','Nonnegative integer contingency counts; uncorrected Pearson χ² and Cramér’s V. Independent observations; sparse counts can invalidate χ² p values.','음이 아닌 정수 분할표 빈도; 보정 없는 Pearson χ²·Cramér의 V. 독립 관측; 희소 빈도에서는 χ² p값이 부정확할 수 있습니다.'),
 ('phi','Phi coefficient','phi 계수','table','','[[20,5],[7,18]]','Signed phi for a 2×2 integer count table; swapping one category order reverses its sign. No Yates correction.','2×2 정수 빈도표의 부호 있는 phi; 한 변수의 범주 순서를 바꾸면 부호가 반전됩니다. Yates 보정 없음.'),
 ('cohenkappa','Cohen’s κ agreement','Cohen의 κ 일치도','table',',unweighted','[[25,4,2],[3,20,5],[1,6,24]]','Two-rater square count table with common category order. Unweighted, linear or quadratic kappa; multinomial delta-method SE and asymptotic Wald CI. Weighted categories must be ordered. Observed/expected agreement use the selected weights; exact agreement is also reported. Distinct from Bayesian prior strength kappa.','공통 범주 순서의 두 평가자 정방 빈도표. 무가중·선형·제곱 가중 κ; 다항 델타법 SE·점근 Wald CI. 가중 범주는 순서형이어야 합니다. 관측·기대 일치율은 선택한 가중치를 반영하며 정확 일치율도 제공합니다. 베이지안 사전 강도 kappa와 별도 기능입니다.'),
 ('dunn','Dunn post-hoc test','Dunn 사후검정','table',',holm','[[1,2,4],[2,3,6],[3,5,8],[4,7,9]]','List of independent sample lists, then holm (default), bonferroni, fdr or none. Pooled midranks, tie correction, two-sided normal p values. Retains the Kruskal–Wallis pooled rank scale.','독립 표본 목록들의 목록과 holm(기본)·bonferroni·fdr·none. 전체 평균순위·동점 보정·양측 정규 p값. Kruskal–Wallis의 전체 순위 척도를 유지합니다.'),
 ('discriminantanalysis','Discriminant analysis (LDA/QDA)','판별분석 (LDA/QDA)','table',',lda,empirical','[[1,2,1],[2,1,1],[1,1,1],[2,3,1],[4,5,2],[5,4,2],[4,4,2],[5,6,2]]','Rows: features, class. LDA pooled / QDA separate unbiased covariances; empirical/equal priors. Optional fourth argument: new feature rows. Training confusion and accuracy are resubstitution, not validation. No automatic regularization.','열: 변수·분류. LDA 합동 / QDA 개별 불편 공분산; 경험·동일 사전확률. 선택적 넷째 인수: 새 변수 행. 학습 혼동표·정확도는 재대입 평가이며 검증이 아닙니다. 자동 정규화 없음.'),
 ('quantreg','Quantile regression','분위회귀','table',',0.5',REG,'Rows: predictors, response; quantile in (0,1). IRLS with subgradient optimality check; asymptotic Gaussian-kernel sandwich / Hall–Sheather bandwidth if residual density supports inference.','열: 설명변수·반응; (0,1)의 분위수. 부분기울기 최적성 검사 IRLS; 잔차 밀도가 추론을 허용하면 Gaussian 커널 샌드위치 / Hall–Sheather 대역폭의 점근 추론.'),
 ('zeroinflated','Zero-inflated regression (ZIP/ZINB)','영과잉 회귀 (ZIP/ZINB)','table',',poisson,intercept',COUNTS,'Rows: predictors, integer counts. Poisson or estimated-alpha NB2 count mixture with logit structural zeros; inflation intercept or same predictors. Joint ML Wald inference; no hurdle model or automatic Vuong test.','열: 설명변수·정수 빈도. Poisson 또는 alpha 추정 NB2와 구조적 0의 logit 혼합; 영과잉 절편 또는 같은 설명변수. 공동 ML Wald 추론; 허들 모형·자동 Vuong 검정 미지원.'),
 ('tobit','Tobit censored regression','Tobit 검열 회귀','table',',0,none','[[0,0],[1,0],[2,1],[3,3],[4,3],[5,6],[6,5],[7,8],[8,9],[9,8],[10,11],[11,12]]','Rows: predictors, observed response; lower bound (default 0), upper bound (default none). Type-I normal censoring, joint ML coefficient/sigma inference. Values at bounds are censored; coefficients refer to the latent response. No truncation/selection model.','열: 설명변수·관측 반응; 하한(기본 0)·상한(기본 none). Type-I 정규 검열·공동 ML 계수/sigma 추론. 경계값은 검열 관측, 계수는 잠재 반응 기준. 절단·선택 모형 미지원.'),
 ('hcluster','Hierarchical clustering','계층적 군집분석','table',',2,ward,1','[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]]','Independent agglomerative analysis; clusters, single/complete/average/Ward Euclidean linkage, standardize 1/0. Returns all merges, heights, sizes and cut assignments. Leaves 1…n, merge nodes n+1…2n−1.','독립 응집 분석; 군집 수·single/complete/average/Ward 유클리드 연결·표준화 1/0. 전체 병합·거리·크기·절단 배정. 잎 1…n, 병합 노드 n+1…2n−1.'),
]

def forms(field,col,multi,group_fields):
    choice=lambda key,en,ko,default,values: field(key,en,ko,'choice',default,[(v,e,k) for v,e,k in values])
    scaling=choice('standardize','Scaling','척도','1',[('1','Standardize','표준화'),('0','Raw scale','원척도')])
    features=[multi('columns','Feature / item columns','변수·문항 열')]
    regression=[col('response','Response','반응 열',-1),multi('predictors','Predictors','설명변수 열')]
    measurement=features+[field('factors','Factor IDs in selected order','선택 순서의 요인 ID','text','1,1,1,2,2,2')]
    layout=choice('layout','Data layout','자료 형태','counts',[('counts','Contingency counts','분할표 빈도'),('pairs','Paired categories','범주 쌍')])
    categorical=[layout,dict(multi('columns','Count columns','빈도 열'),when={'layout':['counts']}),dict(col('first','First variable / rater','첫 변수·평가자',0),when={'layout':['pairs']}),dict(col('second','Second variable / rater','둘째 변수·평가자',1),when={'layout':['pairs']})]
    social=[col('x','Predictor X','설명변수 X',0),col('middle','Mediator M / moderator W','매개 M·조절 W',1),col('response','Outcome Y','결과 Y',-1),field('covariates','Covariate columns (optional)','공변량 열 (선택)','columns','')]
    return {
      'cronbach':features+[choice('mode','Alpha','α','raw',[('raw','Raw','원척도'),('standardized','Standardized','표준화')])],
      'efa':features+[field('factors','Number of factors','요인 수','number','2'),choice('rotation','Rotation','회전','varimax',[('varimax','Varimax (orthogonal)','Varimax (직교)'),('none','None','없음')])],
      'cfa':measurement,'sem':measurement+[field('paths','Latent paths: source,target;…','잠재 경로: 출발,도착;…','text','1,2')],
      'manova':[col('group','Group','그룹 열',0),multi('responses','Response columns','종속변수 열')],
      'mediation':social+[field('samples','Bootstrap samples','부트스트랩 횟수','number','2000'),field('seed','Seed','시드','number','0')],
      'moderation':social,'cramerv':categorical,'phi':categorical,
      'cohenkappa':categorical+[choice('weights','Weights','가중치','unweighted',[('unweighted','Unweighted','무가중'),('linear','Linear','선형'),('quadratic','Quadratic','제곱')]),dict(field('categories','Category order (comma separated; optional for unweighted)','범주 순서 (쉼표 구분; 무가중은 선택)','text',''),when={'layout':['pairs']})],
      'dunn':group_fields+[choice('adjustment','P-value adjustment','p값 보정','holm',[(v,e,e) for v,e in [('holm','Holm'),('bonferroni','Bonferroni'),('fdr','FDR (BH)'),('none','None')]])],
      'discriminantanalysis':[col('response','Class','분류 열',-1),multi('predictors','Features','변수 열'),choice('method','Method','방법','lda',[('lda','LDA','LDA'),('qda','QDA','QDA')]),choice('prior','Class priors','분류 사전확률','empirical',[('empirical','Empirical','경험 빈도'),('equal','Equal','동일')])],
      'quantreg':regression+[field('quantile','Quantile','분위수','number','0.5')],
      'zeroinflated':regression+[choice('family','Count family','빈도 분포','poisson',[('poisson','Poisson (ZIP)','Poisson (ZIP)'),('nbinom','Negative binomial (ZINB2)','음이항 (ZINB2)')]),choice('inflation','Inflation predictors','영과잉 설명변수','intercept',[('intercept','Intercept only','절편만'),('same','Same predictors','같은 설명변수')])],
      'tobit':regression+[field('lower','Lower bound (none = absent)','하한 (none = 없음)','text','0'),field('upper','Upper bound (none = absent)','상한 (none = 없음)','text','none')],
      'hcluster':features+[field('clusters','Clusters','군집 수','number','2'),choice('linkage','Linkage','연결 방법','ward',[(v,v.title(),v.title()) for v in ('ward','single','complete','average')]),scaling],
    }
