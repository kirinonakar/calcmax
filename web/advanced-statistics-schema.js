export const advancedStatisticsSchema = [
  {
    "id": "padjust",
    "label": "Multiple testing",
    "ko": "다중검정 보정",
    "input": "list",
    "suffix": ",holm,0.05",
    "example": "padjust([0.01,0.04,0.03,0.2],holm,0.05)",
    "help": "p values; method bonferroni / holm / fdr (BH) / by; alpha.",
    "helpKo": "p값 목록; 방법 bonferroni / holm / fdr (BH) / by; 유의수준.",
    "controls": [
      {
        "key": "column",
        "label": "p-value column",
        "ko": "p값 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "method",
        "label": "Correction",
        "ko": "보정 방법",
        "type": "choice",
        "default": "holm",
        "choices": [
          {
            "id": "bonferroni",
            "label": "Bonferroni",
            "ko": "Bonferroni"
          },
          {
            "id": "holm",
            "label": "Holm",
            "ko": "Holm"
          },
          {
            "id": "fdr",
            "label": "FDR (BH)",
            "ko": "FDR (BH)"
          }
        ]
      },
      {
        "key": "alpha",
        "label": "Significance α",
        "ko": "유의수준 α",
        "type": "number",
        "default": "0.05"
      }
    ],
    "formHelp": "Adjust p values from the selected column.",
    "formHelpKo": "선택한 열의 p값을 보정합니다.",
    "exampleRows": [
      [
        "0.01"
      ],
      [
        "0.04"
      ],
      [
        "0.03"
      ],
      [
        "0.2"
      ]
    ]
  },
  {
    "id": "cohend",
    "label": "Cohen’s d",
    "ko": "Cohen의 d",
    "input": "groups",
    "suffix": ",independent",
    "example": "cohend([1,2,4,5],[2,3,5,8],independent)",
    "help": "Two samples; independent (pooled d) or paired (dz).",
    "helpKo": "두 표본; independent(합동 SD) 또는 paired(차이의 SD)."
  },
  {
    "id": "eta2",
    "label": "η² effect size",
    "ko": "η² 효과크기",
    "input": "groups",
    "suffix": "",
    "example": "eta2([1,2,4,5],[2,3,5,8])",
    "help": "Independent groups as separate lists.",
    "helpKo": "독립 그룹별 목록."
  },
  {
    "id": "levene",
    "label": "Levene / Brown–Forsythe",
    "ko": "Levene / Brown–Forsythe",
    "input": "groups",
    "suffix": "",
    "example": "levene([1,2,4,5],[2,3,5,8])",
    "help": "Separate group lists; median-centered equal-variance test.",
    "helpKo": "그룹별 목록; 중앙값 기준 등분산 검정.",
    "controls": [
      {
        "key": "grouping",
        "label": "Grouping",
        "ko": "그룹 구성",
        "type": "choice",
        "default": "columns",
        "choices": [
          {
            "id": "columns",
            "label": "Columns",
            "ko": "열별 그룹"
          },
          {
            "id": "groups",
            "label": "Group / value columns",
            "ko": "그룹·값 열"
          }
        ]
      },
      {
        "key": "columns",
        "label": "Group columns",
        "ko": "그룹 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "grouping": [
            "columns"
          ]
        }
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "value",
        "label": "Value column",
        "ko": "값 열",
        "type": "column",
        "default": 1,
        "when": {
          "grouping": [
            "groups"
          ]
        }
      }
    ],
    "formHelp": "Compare group variances using median centers.",
    "formHelpKo": "중앙값 기준으로 그룹의 분산을 비교합니다.",
    "exampleRows": [
      [
        "1",
        "2"
      ],
      [
        "2",
        "3"
      ],
      [
        "4",
        "5"
      ],
      [
        "5",
        "8"
      ]
    ]
  },
  {
    "id": "bartlett",
    "label": "Bartlett",
    "ko": "Bartlett",
    "input": "groups",
    "suffix": "",
    "example": "bartlett([1,2,4,5],[2,3,5,8])",
    "help": "Separate group lists; normality assumption.",
    "helpKo": "그룹별 목록; 정규성 가정.",
    "controls": [
      {
        "key": "grouping",
        "label": "Grouping",
        "ko": "그룹 구성",
        "type": "choice",
        "default": "columns",
        "choices": [
          {
            "id": "columns",
            "label": "Columns",
            "ko": "열별 그룹"
          },
          {
            "id": "groups",
            "label": "Group / value columns",
            "ko": "그룹·값 열"
          }
        ]
      },
      {
        "key": "columns",
        "label": "Group columns",
        "ko": "그룹 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "grouping": [
            "columns"
          ]
        }
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "value",
        "label": "Value column",
        "ko": "값 열",
        "type": "column",
        "default": 1,
        "when": {
          "grouping": [
            "groups"
          ]
        }
      }
    ],
    "formHelp": "Compare variances of normally distributed groups.",
    "formHelpKo": "정규분포를 가정해 그룹의 분산을 비교합니다.",
    "exampleRows": [
      [
        "1",
        "2"
      ],
      [
        "2",
        "3"
      ],
      [
        "4",
        "5"
      ],
      [
        "5",
        "8"
      ]
    ]
  },
  {
    "id": "mcnemar",
    "label": "McNemar",
    "ko": "McNemar",
    "input": "table",
    "suffix": ",exact",
    "example": "mcnemar([[20,8],[2,15]],exact)",
    "help": "Paired 2×2 count table; exact / corrected / asymptotic.",
    "helpKo": "대응 2×2 빈도표; exact / corrected / asymptotic.",
    "controls": [
      {
        "key": "layout",
        "label": "Data",
        "ko": "자료 형태",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "2×2 counts",
            "ko": "2×2 빈도표"
          },
          {
            "id": "pairs",
            "label": "Paired observations",
            "ko": "대응 관측값"
          }
        ]
      },
      {
        "key": "first",
        "label": "Before / first",
        "ko": "이전·첫째 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "second",
        "label": "After / second",
        "ko": "이후·둘째 열",
        "type": "column",
        "default": 1
      },
      {
        "key": "method",
        "label": "Method",
        "ko": "검정 방법",
        "type": "choice",
        "default": "exact",
        "choices": [
          {
            "id": "exact",
            "label": "Exact",
            "ko": "정확 검정"
          },
          {
            "id": "corrected",
            "label": "Continuity corrected",
            "ko": "연속성 보정"
          },
          {
            "id": "asymptotic",
            "label": "Asymptotic",
            "ko": "점근 검정"
          }
        ]
      }
    ],
    "formHelp": "Use two paired category columns or a 2×2 count table.",
    "formHelpKo": "두 대응 범주 열 또는 2×2 빈도표를 사용합니다.",
    "exampleRows": [
      [
        "20",
        "8"
      ],
      [
        "2",
        "15"
      ]
    ]
  },
  {
    "id": "kaplanmeier",
    "label": "Kaplan–Meier",
    "ko": "Kaplan–Meier",
    "input": "table",
    "suffix": ",0.95",
    "example": "kaplanmeier([[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]],0.95)",
    "help": "Rows: time, event (1=event, 0=censored); confidence level.",
    "helpKo": "열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.",
    "controls": [
      {
        "key": "time",
        "label": "Time",
        "ko": "시간 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "event",
        "label": "Event",
        "ko": "사건 열",
        "type": "column",
        "default": 1
      },
      {
        "key": "eventValue",
        "label": "Event value",
        "ko": "사건 발생 값",
        "type": "number",
        "default": "1"
      },
      {
        "key": "level",
        "label": "Confidence level",
        "ko": "신뢰수준",
        "type": "number",
        "default": "0.95"
      }
    ],
    "formHelp": "Choose time and event columns; other event values are censored.",
    "formHelpKo": "시간·사건 열을 선택합니다. 발생 값 이외는 중도절단입니다.",
    "exampleRows": [
      [
        "1",
        "1"
      ],
      [
        "2",
        "0"
      ],
      [
        "3",
        "1"
      ],
      [
        "4",
        "1"
      ],
      [
        "5",
        "0"
      ],
      [
        "6",
        "1"
      ]
    ]
  },
  {
    "id": "logrank",
    "label": "Log-rank",
    "ko": "로그순위 검정",
    "input": "survivalgroups",
    "suffix": "",
    "example": "logrank([[1,1],[3,1],[4,0],[6,1]],[[2,0],[4,1],[5,1],[7,0]])",
    "help": "Two time/event tables. Current data: time, event, group (exactly two groups).",
    "helpKo": "두 시간/사건 표. 현재 데이터 열: 시간, 사건, 그룹(2개).",
    "controls": [
      {
        "key": "time",
        "label": "Time",
        "ko": "시간 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "event",
        "label": "Event",
        "ko": "사건 열",
        "type": "column",
        "default": 1
      },
      {
        "key": "eventValue",
        "label": "Event value",
        "ko": "사건 발생 값",
        "type": "number",
        "default": "1"
      },
      {
        "key": "group",
        "label": "Group",
        "ko": "그룹 열",
        "type": "column",
        "default": 2
      }
    ],
    "formHelp": "Compare exactly two groups; other event values are censored.",
    "formHelpKo": "두 그룹을 비교합니다. 발생 값 이외는 중도절단입니다.",
    "exampleRows": [
      [
        "1",
        "1",
        "1"
      ],
      [
        "3",
        "1",
        "1"
      ],
      [
        "4",
        "0",
        "1"
      ],
      [
        "6",
        "1",
        "1"
      ],
      [
        "2",
        "0",
        "2"
      ],
      [
        "4",
        "1",
        "2"
      ],
      [
        "5",
        "1",
        "2"
      ],
      [
        "7",
        "0",
        "2"
      ]
    ]
  },
  {
    "id": "survivalanalysis",
    "label": "Survival analysis",
    "ko": "생존분석",
    "input": "table",
    "suffix": ",0,efron,-1,1",
    "example": "survivalanalysis([[1,1,1],[2,1,2],[3,0,1],[4,1,2],[5,1,1],[6,0,2],[7,1,2],[8,1,1]],0,efron,-1,1)",
    "help": "Rows: time, event (0/1), group ID, optional Cox predictors; Cox 0=off, 1=on; then ties and the PH check.",
    "helpKo": "열: 시간, 사건(0/1), 그룹 ID, 선택적 Cox 설명변수. Cox 0=끔, 1=켬; 이어서 동률 처리와 PH 검정.",
    "controls": [
      {
        "key": "time",
        "label": "Time",
        "ko": "시간 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "event",
        "label": "Event",
        "ko": "사건 열",
        "type": "column",
        "default": 1
      },
      {
        "key": "eventValue",
        "label": "Event value",
        "ko": "사건 발생 값",
        "type": "number",
        "default": "1"
      },
      {
        "key": "grouping",
        "label": "Groups",
        "ko": "그룹",
        "type": "choice",
        "default": "groups",
        "choices": [
          {
            "id": "groups",
            "label": "Group column",
            "ko": "그룹 열"
          },
          {
            "id": "all",
            "label": "All subjects",
            "ko": "전체 대상"
          }
        ]
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "그룹 열",
        "type": "column",
        "default": 2,
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "cox",
        "label": "Cox model",
        "ko": "Cox 모형",
        "type": "choice",
        "default": "0",
        "choices": [
          {
            "id": "0",
            "label": "Off",
            "ko": "끔"
          },
          {
            "id": "1",
            "label": "On",
            "ko": "켬"
          }
        ]
      },
      {
        "key": "predictors",
        "label": "Cox predictors",
        "ko": "Cox 설명변수 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "cox": [
            "1"
          ]
        }
      },
      {
        "key": "ties",
        "label": "Tie handling",
        "ko": "동률 처리",
        "type": "choice",
        "default": "efron",
        "choices": [
          {
            "id": "efron",
            "label": "Efron",
            "ko": "Efron"
          },
          {
            "id": "breslow",
            "label": "Breslow",
            "ko": "Breslow"
          }
        ]
      },
      {
        "key": "ph",
        "label": "Proportional-hazards check",
        "ko": "비례위험 검정",
        "type": "choice",
        "default": "test",
        "choices": [
          {
            "id": "test",
            "label": "Schoenfeld test",
            "ko": "Schoenfeld 검정"
          },
          {
            "id": "none",
            "label": "Skip",
            "ko": "생략"
          }
        ],
        "when": {
          "cox": [
            "1"
          ]
        }
      }
    ],
    "formHelp": "Kaplan–Meier curves · log-rank · Cox; other event values are censored.",
    "formHelpKo": "Kaplan–Meier 곡선 · log-rank · Cox. 발생 값 이외는 중도절단입니다.",
    "exampleRows": [
      [
        "1",
        "1",
        "1"
      ],
      [
        "2",
        "1",
        "2"
      ],
      [
        "3",
        "0",
        "1"
      ],
      [
        "4",
        "1",
        "2"
      ],
      [
        "5",
        "1",
        "1"
      ],
      [
        "6",
        "0",
        "2"
      ],
      [
        "7",
        "1",
        "2"
      ],
      [
        "8",
        "1",
        "1"
      ]
    ]
  },
  {
    "id": "cox",
    "label": "Cox regression",
    "ko": "Cox 회귀",
    "input": "table",
    "suffix": ",efron,-1,1",
    "example": "cox([[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]],efron,-1,1)",
    "help": "Rows: time, event 0/1, predictors. Ties efron (default) or breslow; entry column for left truncation (-1 none); PH check 0/1.",
    "helpKo": "열: 시간, 사건 0/1, 설명변수. 동률 efron(기본)/breslow, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.",
    "controls": [
      {
        "key": "time",
        "label": "Time",
        "ko": "시간 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "event",
        "label": "Event",
        "ko": "사건 열",
        "type": "column",
        "default": 1
      },
      {
        "key": "eventValue",
        "label": "Event value",
        "ko": "사건 발생 값",
        "type": "number",
        "default": "1"
      },
      {
        "key": "predictors",
        "label": "Predictors",
        "ko": "설명변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "ties",
        "label": "Tie handling",
        "ko": "동률 처리",
        "type": "choice",
        "default": "efron",
        "choices": [
          {
            "id": "efron",
            "label": "Efron",
            "ko": "Efron"
          },
          {
            "id": "breslow",
            "label": "Breslow",
            "ko": "Breslow"
          }
        ]
      },
      {
        "key": "truncation",
        "label": "Left truncation",
        "ko": "좌측 절단",
        "type": "choice",
        "default": "none",
        "choices": [
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          },
          {
            "id": "entry",
            "label": "Entry-time column",
            "ko": "진입시간 열"
          }
        ]
      },
      {
        "key": "entry",
        "label": "Entry time",
        "ko": "진입시간 열",
        "type": "column",
        "default": 2,
        "when": {
          "truncation": [
            "entry"
          ]
        }
      },
      {
        "key": "ph",
        "label": "Proportional-hazards check",
        "ko": "비례위험 검정",
        "type": "choice",
        "default": "test",
        "choices": [
          {
            "id": "test",
            "label": "Schoenfeld test",
            "ko": "Schoenfeld 검정"
          },
          {
            "id": "none",
            "label": "Skip",
            "ko": "생략"
          }
        ]
      }
    ],
    "formHelp": "Proportional hazards; Breslow/Efron ties, optional entry column for left truncation and a scaled-Schoenfeld PH check.",
    "formHelpKo": "비례위험; Breslow/Efron 동률, 선택적 진입시간 열(좌측 절단), 스케일된 Schoenfeld PH 검정.",
    "exampleRows": [
      [
        "1",
        "1",
        "0"
      ],
      [
        "2",
        "1",
        "1"
      ],
      [
        "3",
        "0",
        "0"
      ],
      [
        "4",
        "1",
        "1"
      ],
      [
        "5",
        "1",
        "0"
      ],
      [
        "6",
        "0",
        "1"
      ],
      [
        "7",
        "1",
        "1"
      ],
      [
        "8",
        "1",
        "0"
      ]
    ]
  },
  {
    "id": "repeatedanova",
    "label": "Repeated-measures ANOVA",
    "ko": "반복측정 ANOVA",
    "input": "table",
    "suffix": ",1",
    "example": "repeatedanova([[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]],1)",
    "help": "Rows=subjects, columns=conditions. Second-factor levels: 1 = one-way, 2+ = two-way (first factor slowest); GG corrections.",
    "helpKo": "행=대상, 열=조건. 둘째 요인 수준: 1=일요인, 2 이상=이요인(첫 요인 최외곽); GG 보정.",
    "controls": [
      {
        "key": "columns",
        "label": "Condition columns",
        "ko": "조건 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "factor2",
        "label": "Second-factor levels",
        "ko": "둘째 요인 수준",
        "type": "number",
        "default": "1"
      }
    ],
    "formHelp": "One row per subject; one or two within factors with GG corrections.",
    "formHelpKo": "행마다 한 대상. 일·이요인 반복측정·GG 보정입니다.",
    "exampleRows": [
      [
        "2",
        "4",
        "5"
      ],
      [
        "3",
        "4",
        "7"
      ],
      [
        "4",
        "7",
        "8"
      ],
      [
        "2",
        "3",
        "6"
      ],
      [
        "5",
        "6",
        "7"
      ]
    ]
  },
  {
    "id": "mixedmodel",
    "label": "Mixed model",
    "ko": "혼합모형",
    "input": "table",
    "suffix": ",0",
    "example": "mixedmodel([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],0)",
    "help": "Rows: subject ID, predictors, response. Gaussian random intercept with up to three random slopes (0 none, a predictor position, or [1,2]); third argument ml (default) or reml; up to 5000 rows.",
    "helpKo": "열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기(0 없음, 변수 위치, 또는 [1,2]); 셋째 인수 ml(기본)·reml; 최대 5000행.",
    "controls": [
      {
        "key": "subject",
        "label": "Subject / cluster",
        "ko": "대상·군집 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "response",
        "label": "Response",
        "ko": "반응 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Predictors",
        "ko": "설명변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "slope",
        "label": "Random-slope predictors",
        "ko": "랜덤 기울기 변수",
        "type": "number",
        "default": "0"
      },
      {
        "key": "method",
        "label": "Estimation",
        "ko": "추정 방법",
        "type": "choice",
        "default": "ml",
        "choices": [
          {
            "id": "ml",
            "label": "ML",
            "ko": "ML"
          },
          {
            "id": "reml",
            "label": "REML",
            "ko": "REML"
          }
        ]
      }
    ],
    "formHelp": "Gaussian random intercept with up to three random slopes under ML or REML. Random-slope positions accept 0, a number or a list such as 1,2.",
    "formHelpKo": "Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기, ML·REML 추정입니다. 랜덤 기울기 위치는 0, 번호, 또는 1,2 같은 목록입니다.",
    "exampleRows": [
      [
        "1",
        "0",
        "2"
      ],
      [
        "1",
        "1",
        "4"
      ],
      [
        "1",
        "2",
        "4"
      ],
      [
        "2",
        "0",
        "3"
      ],
      [
        "2",
        "1",
        "4"
      ],
      [
        "2",
        "2",
        "6"
      ],
      [
        "3",
        "0",
        "1"
      ],
      [
        "3",
        "1",
        "3"
      ],
      [
        "3",
        "2",
        "4"
      ],
      [
        "4",
        "0",
        "4"
      ],
      [
        "4",
        "1",
        "5"
      ],
      [
        "4",
        "2",
        "8"
      ]
    ]
  },
  {
    "id": "gee",
    "label": "GEE",
    "ko": "GEE",
    "input": "table",
    "suffix": ",gaussian,independence",
    "example": "gee([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],gaussian,independence)",
    "help": "Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independent / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE.",
    "helpKo": "열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE.",
    "controls": [
      {
        "key": "subject",
        "label": "Subject / cluster",
        "ko": "대상·군집 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "response",
        "label": "Response",
        "ko": "반응 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Predictors",
        "ko": "설명변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "family",
        "label": "Family",
        "ko": "분포",
        "type": "choice",
        "default": "gaussian",
        "choices": [
          {
            "id": "gaussian",
            "label": "Gaussian",
            "ko": "Gaussian"
          },
          {
            "id": "binomial",
            "label": "Binomial (0/1)",
            "ko": "이항 (0/1)"
          },
          {
            "id": "poisson",
            "label": "Poisson",
            "ko": "포아송"
          }
        ]
      },
      {
        "key": "corr",
        "label": "Working correlation",
        "ko": "작업상관",
        "type": "choice",
        "default": "independence",
        "choices": [
          {
            "id": "independence",
            "label": "Independent",
            "ko": "독립"
          },
          {
            "id": "exchangeable",
            "label": "Exchangeable",
            "ko": "교환가능"
          },
          {
            "id": "ar1",
            "label": "AR(1)",
            "ko": "AR(1)"
          }
        ]
      },
      {
        "key": "interactions",
        "label": "Interactions (columns or names)",
        "ko": "상호작용 (열·이름)",
        "type": "number",
        "default": ""
      }
    ],
    "formHelp": "Working correlation independent / exchangeable / AR(1); cluster-robust SE. Interactions accept header names (age,weight), the shown column letters or labels (y,z / age (y),weight (z)), column numbers (2,3) or predictor order (p1,p2); separate pairs with ;.",
    "formHelpKo": "작업상관 independent / exchangeable / AR(1); 군집 강건 표준오차. 상호작용은 열 이름(age,weight), 표시된 열 문자·라벨(y,z / age (y),weight (z)), 열 번호(2,3), 설명변수 순서(p1,p2)로 입력하고 쌍은 ;로 구분합니다.",
    "exampleRows": [
      [
        "1",
        "0",
        "2"
      ],
      [
        "1",
        "1",
        "4"
      ],
      [
        "1",
        "2",
        "4"
      ],
      [
        "2",
        "0",
        "3"
      ],
      [
        "2",
        "1",
        "4"
      ],
      [
        "2",
        "2",
        "6"
      ],
      [
        "3",
        "0",
        "1"
      ],
      [
        "3",
        "1",
        "3"
      ],
      [
        "3",
        "2",
        "4"
      ],
      [
        "4",
        "0",
        "4"
      ],
      [
        "4",
        "1",
        "5"
      ],
      [
        "4",
        "2",
        "8"
      ]
    ]
  },
  {
    "id": "multinomial",
    "label": "Multinomial logistic",
    "ko": "다항 로지스틱",
    "input": "table",
    "suffix": "",
    "example": "multinomial([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])",
    "help": "Rows: predictors, numeric category response. Smallest category is reference.",
    "helpKo": "열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준."
  },
  {
    "id": "ordinal",
    "label": "Ordinal logistic",
    "ko": "순서형 로지스틱",
    "input": "table",
    "suffix": "",
    "example": "ordinal([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])",
    "help": "Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.",
    "helpKo": "열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓."
  },
  {
    "id": "poissonreg",
    "label": "Poisson regression",
    "ko": "포아송 회귀",
    "input": "table",
    "suffix": "",
    "example": "poissonreg([[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]])",
    "help": "Rows: predictors, integer count response. Log link.",
    "helpKo": "열: 설명변수, 정수 빈도 반응. 로그 연결함수."
  },
  {
    "id": "nbreg",
    "label": "Negative binomial regression",
    "ko": "음이항 회귀",
    "input": "table",
    "suffix": "",
    "example": "nbreg([[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]])",
    "help": "Rows: predictors, integer count response. NB2 with estimated dispersion.",
    "helpKo": "열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정."
  },
  {
    "id": "bootstrapci",
    "label": "Bootstrap confidence interval",
    "ko": "부트스트랩 신뢰구간",
    "input": "list",
    "suffix": ",mean,0.95,2000,0",
    "example": "bootstrapci([1,2,3,4,5,8],mean,0.95,2000,0)",
    "help": "Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.",
    "helpKo": "통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식."
  },
  {
    "id": "testpower",
    "label": "Power",
    "ko": "검정력",
    "input": "none",
    "suffix": "",
    "example": "testpower(0.5,64,0.05,independent)",
    "help": "Cohen d, n per group/pairs, alpha, independent / paired / onesample, alternative two (default) / greater / less. Exact noncentral-t power.",
    "helpKo": "Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample, 대립가설 two(기본) / greater / less. 정확 noncentral-t 검정력."
  },
  {
    "id": "samplesize",
    "label": "Sample size",
    "ko": "표본수",
    "input": "none",
    "suffix": "",
    "example": "samplesize(0.5,0.8,0.05,independent)",
    "help": "Cohen d, target power, alpha, design, alternative. Exact noncentral-t power.",
    "helpKo": "Cohen d, 목표 검정력, 유의수준, 설계, 대립가설. 정확 noncentral-t 검정력."
  },
  {
    "id": "kstest",
    "label": "Kolmogorov–Smirnov",
    "ko": "Kolmogorov–Smirnov",
    "input": "groups",
    "suffix": "",
    "example": "kstest([1,2,4,5],[2,3,5,8])",
    "help": "Two sample lists, or kstest(data,normal,mu,sigma) / kstest(data,uniform,lower,width). Continuous null; one-sample p is asymptotic.",
    "helpKo": "두 표본 목록 또는 kstest(data,normal,평균,SD) / kstest(data,uniform,하한,폭). 연속분포 가정; 일표본 p는 근사.",
    "controls": [
      {
        "key": "mode",
        "label": "Samples / distribution",
        "ko": "표본·분포",
        "type": "choice",
        "default": "two",
        "choices": [
          {
            "id": "two",
            "label": "Two samples",
            "ko": "두 표본"
          },
          {
            "id": "normal",
            "label": "Normal",
            "ko": "정규분포"
          },
          {
            "id": "uniform",
            "label": "Uniform",
            "ko": "균등분포"
          }
        ]
      },
      {
        "key": "first",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "second",
        "label": "Second sample",
        "ko": "둘째 표본 열",
        "type": "column",
        "default": 1,
        "when": {
          "mode": [
            "two"
          ]
        }
      },
      {
        "key": "location",
        "label": "Mean / lower bound",
        "ko": "평균·하한",
        "type": "number",
        "default": "0",
        "when": {
          "mode": [
            "normal",
            "uniform"
          ]
        }
      },
      {
        "key": "scale",
        "label": "SD / width",
        "ko": "표준편차·폭",
        "type": "number",
        "default": "1",
        "when": {
          "mode": [
            "normal",
            "uniform"
          ]
        }
      }
    ],
    "formHelp": "Compare two samples or a specified continuous distribution.",
    "formHelpKo": "두 표본 또는 지정한 연속분포와 비교합니다.",
    "exampleRows": [
      [
        "1",
        "2"
      ],
      [
        "2",
        "3"
      ],
      [
        "4",
        "5"
      ],
      [
        "5",
        "8"
      ]
    ]
  },
  {
    "id": "crossvalidate",
    "label": "Cross-validation",
    "ko": "교차검증",
    "input": "table",
    "suffix": ",3,0",
    "example": "crossvalidate([[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]],3,0)",
    "help": "Rows: predictors, response; folds, seed; split random (default) / blocked / stratified; model linear (default) / ridge / lasso / elasticnet / logistic; penalty alpha or [alpha,l1 ratio].",
    "helpKo": "열: 설명변수, 반응; 폴드 수, 시드; 분할 random(기본) / blocked / stratified; 모형 linear(기본) / ridge / lasso / elasticnet / logistic; 벌점 alpha 또는 [alpha,l1 비율].",
    "controls": [
      {
        "key": "folds",
        "label": "Folds",
        "ko": "폴드 수",
        "type": "number",
        "default": "3"
      },
      {
        "key": "seed",
        "label": "Seed",
        "ko": "시드",
        "type": "number",
        "default": "0"
      },
      {
        "key": "split",
        "label": "Split",
        "ko": "분할",
        "type": "choice",
        "default": "random",
        "choices": [
          {
            "id": "random",
            "label": "Random",
            "ko": "무작위"
          },
          {
            "id": "blocked",
            "label": "Blocked",
            "ko": "블록"
          },
          {
            "id": "stratified",
            "label": "Stratified",
            "ko": "층화"
          }
        ]
      },
      {
        "key": "model",
        "label": "Model",
        "ko": "모형",
        "type": "choice",
        "default": "linear",
        "choices": [
          {
            "id": "linear",
            "label": "Linear (OLS)",
            "ko": "선형 (OLS)"
          },
          {
            "id": "ridge",
            "label": "Ridge",
            "ko": "Ridge"
          },
          {
            "id": "lasso",
            "label": "Lasso",
            "ko": "Lasso"
          },
          {
            "id": "elasticnet",
            "label": "Elastic net",
            "ko": "Elastic net"
          },
          {
            "id": "logistic",
            "label": "Logistic (0/1)",
            "ko": "로지스틱 (0/1)"
          }
        ]
      },
      {
        "key": "alpha",
        "label": "Penalty α",
        "ko": "벌점 α",
        "type": "number",
        "default": "0.1",
        "when": {
          "model": [
            "ridge",
            "lasso",
            "elasticnet",
            "logistic"
          ]
        }
      },
      {
        "key": "ratio",
        "label": "L1 ratio",
        "ko": "L1 비율",
        "type": "number",
        "default": "0.5",
        "when": {
          "model": [
            "elasticnet"
          ]
        }
      }
    ],
    "formHelp": "Held-out folds fitted on training rows only; choose split, model and penalty.",
    "formHelpKo": "훈련 행으로만 적합하는 홀드아웃 폴드; 분할·모형·벌점을 선택합니다.",
    "exampleRows": [
      [
        "0",
        "1"
      ],
      [
        "1",
        "3"
      ],
      [
        "2",
        "4"
      ],
      [
        "3",
        "7"
      ],
      [
        "4",
        "8"
      ],
      [
        "5",
        "11"
      ],
      [
        "6",
        "12"
      ],
      [
        "7",
        "15"
      ],
      [
        "8",
        "16"
      ]
    ]
  },
  {
    "id": "pca",
    "label": "PCA",
    "ko": "주성분 분석",
    "input": "table",
    "suffix": ",2,1",
    "example": "pca([[1,2],[2,1],[3,4],[4,3],[5,7]],2,1)",
    "help": "Rows=observations, columns=features; components, standardize 1/0.",
    "helpKo": "행=관측, 열=변수; 주성분 수, 표준화 1/0."
  },
  {
    "id": "kmeans",
    "label": "K-means clustering",
    "ko": "K-means 군집",
    "input": "table",
    "suffix": ",2,0",
    "example": "kmeans([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)",
    "help": "Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.",
    "helpKo": "숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도."
  },
  {
    "id": "impute",
    "label": "Missing-value imputation",
    "ko": "결측치 대체",
    "input": "table",
    "suffix": ",mean",
    "example": "impute([[1,NA],[2,4],[NA,6],[4,8]],mean)",
    "help": "NA for missing cells; mean / median / mode / regression / knn with neighbours (default 5). Single imputation.",
    "helpKo": "결측값은 NA; mean / median / mode / regression / knn(이웃 수 기본 5). 단일 대체.",
    "controls": [
      {
        "key": "method",
        "label": "Method",
        "ko": "대체 방법",
        "type": "choice",
        "default": "mean",
        "choices": [
          {
            "id": "mean",
            "label": "Mean",
            "ko": "평균"
          },
          {
            "id": "median",
            "label": "Median",
            "ko": "중앙값"
          },
          {
            "id": "mode",
            "label": "Mode",
            "ko": "최빈값"
          },
          {
            "id": "regression",
            "label": "Regression",
            "ko": "회귀"
          },
          {
            "id": "knn",
            "label": "k-NN",
            "ko": "k-NN"
          }
        ]
      },
      {
        "key": "k",
        "label": "Neighbours",
        "ko": "이웃 수",
        "type": "number",
        "default": "5",
        "when": {
          "method": [
            "knn"
          ]
        }
      }
    ],
    "formHelp": "Fill missing NA cells by mean, median, mode, regression or k-NN.",
    "formHelpKo": "결측값(NA)을 평균·중앙값·최빈값·회귀·k-NN으로 대체합니다.",
    "exampleRows": [
      [
        "1",
        "NA"
      ],
      [
        "2",
        "4"
      ],
      [
        "NA",
        "6"
      ],
      [
        "4",
        "8"
      ]
    ]
  }
];
