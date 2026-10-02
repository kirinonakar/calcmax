import {nativeKorean} from './native-locale.js';
// Source strings are stable translation keys. Math expressions and user code are
// never translated; language switches do not change their values.
const english={
  '계산 엔진 로딩을 다시 시도합니다…':'Retrying engine loading…','계산 엔진 로딩 시간이 초과되었습니다. 다시 로딩을 눌러 주세요.':'Engine loading timed out. Click Reload engine to try again.',
  '작업 모드':'Workspace','그래프':'Graph','방정식':'Equations','행렬':'Matrix','벡터':'Vector','데이터 & 통계':'Data & Statistics','프로그래머':'Programmer','단위 변환':'Units','상수':'Constants','팁 계산':'Tip','환율':'Currency','사용자 함수':'Functions','각도 단위':'Angle unit',
  'WebAssembly 런타임 로딩…':'Loading WebAssembly runtime…','SymPy 계산 엔진 로딩…':'Loading SymPy engine…','계산 엔진 준비 완료 · WASM':'Engine ready · WASM','계산 엔진 재시작…':'Restarting engine…','계산 엔진이 로딩 중입니다.':'The engine is still loading.','계산 중입니다. 중지한 뒤 다시 실행해 주세요.':'A calculation is running. Stop it before trying again.','계산 시간이 20초를 초과했습니다.':'Calculation exceeded the 20-second limit.','계산이 중지되었습니다.':'Calculation cancelled.','계산 엔진을 재시작합니다.':'Restarting the calculation engine.','다시 로딩':'Reload engine','중지':'Stop','엔진을 시작할 수 없습니다. 정적 서버와 빌드 파일을 확인해 주세요.':'Could not start the engine. Check the static server and build files.',
  '계산할 수식':'Expression','수식을 입력하세요 · 1/3 + 1/6':'Enter an expression · 1/3 + 1/6','종류':'Type','매개변수 (x(t), y(t))':'Parametric (x(t), y(t))','극좌표 r = f(t)':'Polar r = f(t)','수열 u(n)':'Sequence u(n)','미분방정식 dy/dt':'Differential equation dy/dt','그리기':'Plot','수식 · 한 줄에 하나':'Expressions · one per line','x / t / n 최소':'x / t / n minimum','최대':'Maximum','y 최소':'y minimum','수열 초기값 / 미분방정식 y₀':'Sequence seeds / differential y₀','매개변수: (cos(t),sin(t)) · 극좌표: 1+cos(t) · 수열: u(n-1)+1 · 3D: sin(x)*cos(y)':'Parametric: (cos(t),sin(t)) · Polar: 1+cos(t) · Sequence: u(n-1)+1 · 3D: sin(x)*cos(y)','[shade] y < x^2 또는 [shade] sin(x),cos(x) ; 0..pi 로 영역을 표시합니다.':'Use [shade] y < x^2 or [shade] sin(x),cos(x) ; 0..pi to shade a region.','계산된 함수 그래프':'Calculated function graph','좌표 표':'Coordinate table',
  '실행':'Run','Python 코드':'Python code','input() 입력값 · 한 줄에 하나':'input() values · one per line','스크립트가 요청할 순서대로 입력':'Enter values in the order the script will request them','CPython 표준 라이브러리, sympy, mpmath, calcmax_catalog를 사용할 수 있습니다. 실행 제한은 20초입니다.':'Use the CPython standard library, sympy, mpmath, and calcmax_catalog. Execution is limited to 20 seconds.',
  '방법':'Method','정확한 해 / 연립방정식':'Exact solutions / systems','수치 해':'Numerical solution','상미분방정식':'Ordinary differential equation','편미분방정식':'Partial differential equation','풀기':'Solve','방정식 · 연립방정식은 한 줄씩':'Equations · one per line for systems','변수 / 미지 함수':'Variables / unknown function','x,y 또는 y(t)':'x,y or y(t)','수치 초기 구간 / 미분 변수':'Initial bracket / differentiation variable','0,1 또는 t':'0,1 or t','행 / 성분':'Rows / components','열':'Column','연산':'Operation','계산':'Calculate','두 번째 벡터':'Second vector','각 칸에 분수, 기호, 수식을 입력할 수 있습니다. 추가 행렬 연산은 Catalog에서 사용할 수 있습니다.':'Enter fractions, symbols, or expressions in each cell. More matrix operations are available in Catalog.',
  '저장 데이터':'Saved datasets','새 데이터':'New dataset','데이터 이름':'Dataset name','저장':'Save','삭제':'Delete','CSV 가져오기':'Import CSV','CSV 내보내기':'Export CSV','데이터 · 한 행에 한 관측값, 열은 쉼표로 구분':'Data · one observation per row, comma-separated columns','분석':'Analyze','요약 통계':'Summary statistics','평균':'Mean','중앙값':'Median','표준편차':'Standard deviation','회귀':'Regression','상관계수':'Correlation','단일 표본 t 검정':'One-sample t test','독립 표본 t 검정':'Independent t test','대응 표본 t 검정':'Paired t test','t 신뢰구간':'t confidence interval','일원 분산분석':'One-way ANOVA','Tukey 다중 비교':'Tukey comparisons','정규성 검정':'Normality test','회귀 유형':'Regression model','가설 평균 / 신뢰수준':'Null mean / confidence level','진법':'Base','비트':'Bits','변환':'Convert','B / 시프트 수':'B / shift count','값':'Value','원래 단위':'From unit','변환 단위':'To unit','복합 단위도 계산할 수 있습니다: convert(qty(1,V)/qty(1,ohm),A)':'Compound units are supported: convert(qty(1,V)/qty(1,ohm),A)','상수 불러오기':'Load constants','금액':'Amount','팁 %':'Tip %','인원':'People','1인당 금액':'Per person','기준 통화':'From currency','대상 통화':'To currency','수동 환율':'Manual rate','일일 환율 받기':'Get daily rates','수동 환율을 사용합니다. 온라인 환율은 버튼을 누르면 가져옵니다.':'Using a manual rate. Click Get daily rates to fetch online rates.',
  '함수 이름':'Function name','매개변수 · 쉼표로 구분':'Parameters · comma-separated','수식':'Expression','JSON 내보내기':'Export JSON','JSON 가져오기':'Import JSON','계산 결과':'Calculation result','결과 복사':'Copy result','Ans 삽입':'Insert Ans','계산 결과가 여기에 표시됩니다.':'Your result will appear here.','웹 버전 안내':'Web guide','오픈 소스 라이선스':'Open-source licenses','닫기':'Close','계산 오류':'Calculation error','복사했습니다.':'Copied.','복사할 텍스트':'Text to copy','붙여넣기를 허용하거나 Ctrl+V를 사용해 주세요.':'Allow clipboard access or use Ctrl+V.','먼저 재사용 가능한 결과를 계산해 주세요.':'Calculate a reusable result first.','M에 저장했습니다.':'Stored in M.','수식 또는 결과 검색':'Search expressions or results','기록 검색':'Search history','즐겨찾기만':'Favorites only','전체 기록':'All history','사용':'Use','기록이 없습니다.':'No history yet.','기록 삭제':'Clear history','함수 검색':'Search functions','빈 인수에 값을 입력하세요. 예: diff(sin(x^2),x), normcdf(-1.96,1.96)':'Fill in empty arguments. Examples: diff(sin(x^2),x), normcdf(-1.96,1.96)','함수 도움말':'Function help',
  '변수 이름 · A, b, M':'Variable name · A, b, M','변수 이름':'Variable name','값 또는 수식':'Value or expression','변수 수식':'Variable expression','Ans 이외의 영문 변수 이름을 입력하세요.':'Enter an English variable name other than Ans.','삽입':'Insert','수식 저장':'Store expression','현재 결과 STO':'STO current result','저장 가능한 결과가 없습니다.':'No reusable result to store.','내부 유효 숫자':'Internal significant digits','표시 소수 자릿수':'Displayed decimal places','계산 기록, 변수, 함수, 작업 내용은 이 브라우저에 저장됩니다. 전체 백업에는 Python 코드도 포함됩니다.':'History, variables, functions, and drafts are stored in this browser. Full backups also include your Python code.','전체 백업 내보내기':'Export full backup','백업 가져오기':'Import backup','CalcMax 웹 백업 파일이 아닙니다.':'This is not a CalcMax web backup.','백업을 저장할 공간이 부족합니다.':'Not enough storage to restore the backup.','변수 초기화':'Clear variables','변수를 초기화했습니다.':'Variables cleared.','브라우저 저장 공간을 사용할 수 없어 이번 세션에서만 보관합니다.':'Browser storage is unavailable. Changes will last for this session only.',
  '데이터 이름을 입력하세요.':'Enter a dataset name.','데이터를 저장했습니다.':'Dataset saved.','1~5000행의 데이터를 입력하세요.':'Enter 1–5000 rows of data.','각 행의 열 수를 맞추고 빈 값을 채워 주세요. 최대 3열을 사용할 수 있습니다.':'Use the same number of columns in every row, fill empty cells, and use at most 3 columns.','선택한 열이 데이터에 없습니다.':'The selected column does not exist.','회귀에는 x,y 두 열이 필요합니다.':'Regression requires x,y columns.','이 분석에는 두 열이 필요합니다.':'This analysis requires two columns.','이 분석에는 두 열 이상이 필요합니다.':'This analysis requires at least two columns.','저장된 수식':'Stored expression','편집':'Edit','함수와 매개변수에 서로 다른 영문 이름을 사용하세요.':'Use English names and distinct parameter names.','함수 수식이 올바르지 않습니다.':'The function expression is invalid.','함수를 저장했습니다.':'Function saved.','함수 JSON 객체가 필요합니다.':'A function JSON object is required.','함수를 가져왔습니다.':'Functions imported.',
  '영역 표시는 y=f(x) 그래프에서 사용할 수 있습니다.':'Shading is available for Cartesian graphs.','음영 구간은 a..b 형식으로 입력하세요.':'Enter a shading interval as a..b.','그래프 수식을 입력하세요.':'Enter a graph expression.','최대값은 최소값보다 커야 합니다.':'Each maximum must be greater than its minimum.','곡선':'Curve','3자리 통화 코드를 입력하세요.':'Enter 3-letter currency codes.','환율 요청에 실패했습니다.':'The exchange-rate request failed.','환율을 가져올 수 없습니다.':'Could not fetch exchange rates.','지원하지 않는 대상 통화입니다.':'The target currency is not supported.','그래프 계산 완료':'Graph calculated.','실행 중…':'Running…','실행 완료':'Execution complete.','방정식을 입력하세요.':'Enter an equation.','인원은 1 이상의 정수여야 합니다.':'The number of people must be a positive integer.','Catalog가 없습니다. 빌드 스크립트를 실행해 주세요.':'Catalog is missing. Run the build script.','WebAssembly는 정적 HTTP 서버가 필요합니다. web 폴더에서 python -m http.server 8080을 실행하고 http://localhost:8080을 여세요.':'WebAssembly requires a static HTTP server. Run python -m http.server 8080 in web and open http://localhost:8080.','오프라인 사용 가능':'Available offline','브라우저 캐시 사용':'Using browser cache',
  '언어':'Language','테마':'Theme','라이트':'Light','다크':'Dark','시스템':'System','그래프 범위를 확인해 주세요.':'Check the graph ranges.','통계 값':'Statistical values','계산 엔진 로딩 완료':'Engine loaded',
  'WebAssembly는 정적 HTTP 서버가 필요합니다. python web/serve.py를 실행하고 http://localhost:8080을 여세요.':'WebAssembly requires a static HTTP server. Run python web/serve.py and open http://localhost:8080.',
  'CalcMax WebAssembly 과학·CAS·그래프·Python 계산기':'CalcMax WebAssembly scientific, CAS, graphing, and Python calculator'
};
const korean={
  'Use [shade] y < f(x) to shade below a function, or [shade] f, g to shade between two functions. Example: [shade] sin(x), cos(x), 0..pi.':'[shade] y < f(x)는 함수 아래의 영역을, [shade] f, g는 두 함수 사이의 영역을 표시합니다. 예: [shade] sin(x), cos(x), 0..pi.',
  'Double height':'높이 2배',
  'Implicit Graph':'Implicit Graph(음함수 그래프)','Implicit Graph: x^2+y^2=1':'음함수 그래프: x^2+y^2=1',History:'기록',Catalog:'함수 목록',Setup:'설정',Undo:'실행 취소',Copy:'복사',Paste:'붙여넣기',New:'새 파일','Open .py':'.py 열기','Save .py':'.py 저장',Recent:'최근',Favorites:'즐겨찾기',Custom:'사용자 함수',Scientific:'과학',Symbolic:'기호','Complex':'복소수','ODE & transforms':'미분방정식 & 변환','Vector calculus':'벡터 미적분','Matrix & vector':'행렬 & 벡터','Data & units':'데이터 & 단위',Distributions:'확률 분포','Tests & intervals':'검정 & 구간',Finance:'금융',Signed:'부호 있음','SCIENTIFIC / CAS':'과학 / CAS',
  Cut:'잘라내기',Keyboard:'키보드','Math input':'수학 입력','Next input starts a new calculation':'다음 입력 시 새 계산 시작','Cursor controls':'방향 키','Cursor up':'상위 수식 선택','Cursor down':'하위 수식 선택','Cursor left':'커서 왼쪽','Cursor right':'커서 오른쪽','Insert mode':'삽입 모드',
  'CALC · enter a value, then press = · AC cancels':'CALC · 값을 입력하고 = 누르기 · AC는 취소','Enter a numeric value':'숫자 값을 입력하세요.',
  'Calculation history, scroll vertically':'계산 기록, 위아래로 스크롤','Reuse calculation':'계산 다시 사용','Result notation':'결과 표시 형식',
  'Undefined or division by zero':'정의되지 않거나 0으로 나누는 계산입니다.','Singular matrix':'역행렬을 구할 수 없는 행렬입니다.','Matrix dimension mismatch':'행렬 크기가 맞지 않습니다.','Calculation cancelled':'계산이 중지되었습니다.','Computation limit reached. Reduce expression complexity.':'계산 한도를 초과했습니다. 수식을 단순하게 바꿔 주세요.',
  'Engine archive is missing. Run python web/build.py.':'engine.zip이 없습니다. python web/build.py를 실행해 주세요.',
  'Not enough input values. Enter one value per line in Python inputs.':'입력값이 부족합니다. Python 입력란에 한 줄씩 입력해 주세요.'
};
Object.assign(korean,nativeKorean,{
  "Help": "도움말",
  "Analyzed groups": "분석 그룹",
  "Compared columns": "비교 열",
  "Complete pairs": "완전한 대응 쌍",
  "Enter data to see analyzed groups": "데이터를 입력하면 분석 그룹이 표시됩니다.",
  "Data type": "데이터 형식",
  "List": "리스트",
  "x,y data": "x,y 데이터",
  "x,y,z data": "x,y,z 데이터",
  "x, y values": "x, y 값",
  "x, y, z values": "x, y, z 값",
  "Regression needs x,y data": "회귀에는 x,y 데이터가 필요합니다.",
  "Main keys": "기본 키",
  "2nd keys": "2nd 키",
  "Number keys": "숫자 키",
  "Keypad": "입력패드",
  "No matching buttons": "일치하는 버튼이 없습니다.",
  "ENG mode · ←/→ shifts mantissa": "ENG 모드 · ←/→로 가수 이동",
  "Clear search": "검색어 지우기",
  "Stored in": "저장한 변수:",
  "Reserved constant or answer name": "상수 또는 Ans 이름에는 저장할 수 없습니다.",
  "No matching entries": "일치하는 항목이 없습니다.",
  "Could not load function help": "함수 도움말을 불러올 수 없습니다.",
  "Retry": "다시 시도",
  "Equation type": "방정식 유형",
  "General / system": "일반식 / 연립방정식",
  "Linear": "일차",
  "Quadratic": "이차",
  "Cubic": "삼차",
  "View x min": "보기 x min",
  "View x max": "보기 x max",
  "Zoom in": "확대",
  "Zoom out": "축소",
  "Pan left": "왼쪽 이동",
  "Pan right": "오른쪽 이동",
  "Pan up": "위로 이동",
  "Pan down": "아래로 이동",
  "Fit": "범위 맞춤",
  "Selected curve": "선택한 곡선",
  "Derivative curve": "도함수 그래프",
  "Analysis": "분석",
  "Other curve": "다른 곡선",
  "a / point": "a / 지점",
  "Clear analysis": "분석 지우기",
  "Slider minimum": "슬라이더 최솟값",
  "Parameter value": "매개변수 값",
  "Enter a finite value between -1e9 and 1e9": "-1e9부터 1e9 사이의 유한한 값을 입력하세요.",
  "Slider maximum": "슬라이더 최댓값",
  "Vertical tangent": "수직 접선",
  "No points found in this interval": "이 구간에서 찾은 점이 없습니다.",
  "Tap to trace · drag to pan · pinch or scroll to zoom": "눌러 추적 · 드래그하여 이동 · 두 손가락 또는 휠로 확대/축소",
  "x: decimal": "x: 소수",
  "x: π rad": "x: π 라디안",
  "Grouping": "그룹 지정",
  "x = group, y = value": "x = 그룹, y = 값",
  "One-sample z test": "단일 표본 z 검정",
  "Independent z test": "독립 표본 z 검정",
  "z confidence interval": "z 신뢰구간",
  "χ² independence test": "χ² 독립성 검정",
  "Fisher exact test": "Fisher 정확 검정",
  "Known σ": "알려진 σ",
  "Known σy": "알려진 σy",
  "Plot type": "그래프 유형",
  "Scatter": "산점도",
  "Plot data": "데이터 그리기",
  "Store dataset as variable": "데이터를 변수로 저장",
  "Model y =": "모형 y =",
  "Initial values and bounds (optional)": "초깃값 및 범위 (선택)",
  "Density / P(X = k)": "밀도 / P(X = k)",
  "Cumulative probability": "누적 확률",
  "Interval probability": "구간 확률",
  "Mean μ": "평균 μ",
  "Trials n": "시행 횟수 n",
  "Success probability": "성공 확률",
  "Mean λ": "평균 λ",
  "Other matrix / vector": "다른 행렬 / 벡터",
  "Store grid": "격자 저장",
  "Load variable": "변수 불러오기",
  "Unit category": "단위 분류",
  "All units": "모든 단위",
  "Search constants": "상수 검색",
  "Paste expression": "수식 붙여넣기",
  "Redo": "다시 실행",
  "Indent": "들여쓰기",
  "Outdent": "내어쓰기",
  "Skip header row": "머리글 제외",
  "Select one to three columns": "1~3개 열을 선택하세요.",
  "Symbol assumption": "기호 가정",
  "Set assumption": "가정 설정",
  "Use up to twelve shortcuts": "단축 버튼은 12개까지 설정할 수 있습니다.",
  "Find button or function": "버튼 또는 함수 찾기",
  "Customize display buttons": "표시 버튼 사용자 지정",
  "Swap currencies": "통화 바꾸기",
  "skipped": "제외됨",
  "저장했습니다.": "저장했습니다.",
  "Enter finite values with minimum < maximum": "유한한 최솟값과 최댓값을 입력하세요.",
  "Enter finite values with a < b": "a < b인 유한한 값을 입력하세요.",
  "Enter a valid variable name": "올바른 변수 이름을 입력하세요.",
  "Enter a function to graph": "그릴 함수를 입력하세요.",
  "Shading requires a Cartesian graph": "영역 표시는 직교좌표 그래프에서 가능합니다.",
  "This function name is invalid or reserved": "함수 이름이 올바르지 않거나 예약된 이름입니다.",
  "Enter distinct valid parameter names": "서로 다른 올바른 매개변수 이름을 입력하세요.",
  "About CalcMax": "CalcMax 정보",
  "Choose calculation mode, long press for Scientific/CAS mode": "계산 모드 선택, 길게 누르면 Scientific/CAS 모드로 이동",
  "Whole amounts per person": "1인당 금액을 정수로 보정",
  "Input word wrap": "입력 자동 줄바꿈 (Word wrap)",
  "New": "새로 만들기",
  "Cancel": "취소",
  "Fitting regression…": "회귀 적합 중…",
  "Rendering": "렌더링",
  "Wireframe": "와이어프레임",
  "Surface": "표면",
  "Surface + mesh": "표면+격자",
  "Surface color": "표면 색상",
  "Mesh density": "격자 밀도",
  "Automatic z range": "자동 z 범위",
  "Fit Z": "z 범위 맞춤"
});
for(const [en,ko] of Object.entries(korean))if(!english[ko])english[ko]=en;
const reverse=Object.fromEntries(Object.entries(english).map(([ko,en])=>[en,ko]));
let language='en';
const originals=new WeakMap();
// Saved automatic locale detection is not a user preference. Follow the current
// browser language until the user explicitly selects a language in Setup.
export function initialLanguage(settings={},browserLanguage='en'){
  if(settings.languageChosen===true&&['en','ko'].includes(settings.language))return settings.language;
  return /^ko(?:[-_]|$)/i.test(browserLanguage)?'ko':'en';
}
export function setLanguage(next){language=next==='ko'?'ko':'en';}
export function getLanguage(){return language;}
export function t(key){return language==='en'?(english[key]||key):(korean[key]||reverse[key]||key);}
export function setText(target,key){target.textContent=t(key);if(target.firstChild)originals.set(target.firstChild,key);}
export function translateDOM(root=document.body) {
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
  while(walker.nextNode()) {
    const text=walker.currentNode;
    if(text.parentElement?.closest('script,style,textarea,code,pre,math,#python-output,#expression-preview,#tape-history') || text.parentElement?.closest('#answer')&&!text.parentElement?.closest('.error,.empty'))continue;
    const key=originals.get(text)??text.nodeValue;
    const trimmed=key.trim();
    if(!english[trimmed]&&!korean[trimmed]&&!reverse[trimmed])continue;
    originals.set(text,key);
    text.nodeValue=key.replace(trimmed,t(trimmed));
  }
  for(const el of root.querySelectorAll('[placeholder],[aria-label],[title]'))for(const attr of ['placeholder','aria-label','title']){
    if(!el.hasAttribute(attr))continue;
    const datasetKey=`i18n${attr.replace('-','')}`;
    const key=el.dataset[datasetKey]??el.getAttribute(attr);
    if(!english[key]&&!korean[key]&&!reverse[key])continue;
    el.dataset[datasetKey]=key;el.setAttribute(attr,t(key));
  }
  document.documentElement.lang=language;
  const description=document.querySelector('meta[name="description"]');
  if(description)description.content=t('CalcMax WebAssembly 과학·CAS·그래프·Python 계산기');
}
export function applyTheme(choice,systemDark=false){document.documentElement.dataset.theme=choice==='dark'||choice==='system'&&systemDark?'dark':'light';}
