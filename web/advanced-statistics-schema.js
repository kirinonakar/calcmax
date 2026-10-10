export const advancedStatisticsSchema = [
  {
    "id": "impute",
    "label": "Missing-value imputation",
    "ko": "결측치 대체",
    "input": "table",
    "suffix": ",mean",
    "example": "impute([[1,NA],[2,4],[NA,6],[4,8]],mean)",
    "help": "NA for missing cells; mean / median / mode / regression / knn with neighbours (default 5). Single imputation.",
    "helpKo": "결측값은 NA; mean / median / mode / regression / knn(이웃 수 기본 5). 단일 대체.",
    "section": "preparation",
    "group": "Data preparation",
    "groupKo": "데이터 준비",
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
    "formHelp": "Analyze missing numeric cells by mean, median, mode, regression or k-NN; Apply to current data writes the replacements while retaining observed values and headers. Reanalyze after editing data.",
    "formHelpKo": "숫자 자료의 결측값을 평균·중앙값·최빈값·회귀·k-NN으로 분석합니다. 현재 데이터에 적용하면 관측값·헤더를 유지하며 결측 셀을 실제로 대체합니다. 편집 후에는 다시 분석하세요.",
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
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
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
          },
          {
            "id": "groups",
            "label": "Group / value columns",
            "ko": "그룹·값 열"
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
            "id": "asymptotic",
            "label": "McNemar",
            "ko": "McNemar"
          },
          {
            "id": "exact",
            "label": "Exact McNemar",
            "ko": "Exact McNemar"
          },
          {
            "id": "corrected",
            "label": "Continuity correction",
            "ko": "연속성 보정"
          }
        ]
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
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
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "firstGroup",
        "label": "Group A value",
        "ko": "A 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "secondGroup",
        "label": "Group B value",
        "ko": "B 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "matching",
        "label": "Pair matching",
        "ko": "대응 연결",
        "type": "choice",
        "default": "order",
        "choices": [
          {
            "id": "order",
            "label": "Within-group row order",
            "ko": "그룹 안의 행 순서"
          },
          {
            "id": "subject",
            "label": "Subject ID",
            "ko": "대상 ID"
          }
        ],
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "subject",
        "label": "Subject ID",
        "ko": "대상 ID 열",
        "type": "column",
        "default": 0,
        "when": {
          "matching": [
            "subject"
          ],
          "layout": [
            "groups"
          ]
        }
      }
    ],
    "formHelp": "Use two paired category columns or a 2×2 count table. Choose McNemar (uncorrected chi-square), Exact McNemar (two-sided binomial), or continuity correction. Results show the number of discordant pairs (b + c) and the selected method’s p value together.",
    "formHelpKo": "두 대응 범주 열 또는 2×2 빈도표를 사용합니다. McNemar(보정 없는 χ²), Exact McNemar(양측 이항), 연속성 보정을 선택합니다. 결과에 불일치 쌍의 수(b + c)와 선택한 방법의 p값을 함께 표시합니다.",
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
    "id": "shapiro",
    "label": "Shapiro–Wilk",
    "ko": "Shapiro–Wilk",
    "input": "list",
    "suffix": "",
    "example": "shapiro([1,2,3,4,5])",
    "help": "Normality test for 3 to 5000 observations; interpret with Q–Q plots.",
    "helpKo": "관측값 3~5000개의 정규성 검정; Q–Q plot과 함께 해석합니다.",
    "section": "tests",
    "group": "Distribution & variance",
    "groupKo": "분포·분산 검정",
    "controls": [
      {
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0
      }
    ],
    "formHelp": "Choose a numeric sample column to test normality. Interpret the p value with the Q–Q plot; it does not prove normality.",
    "formHelpKo": "정규성을 검정할 숫자 표본 열을 선택합니다. p값은 Q–Q plot과 함께 해석하며 정규성의 증명이 아닙니다.",
    "exampleRows": [
      [
        "1"
      ],
      [
        "2"
      ],
      [
        "3"
      ],
      [
        "4"
      ],
      [
        "5"
      ]
    ]
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
    "section": "tests",
    "group": "Distribution & variance",
    "groupKo": "분포·분산 검정",
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
      },
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
        ],
        "when": {
          "mode": [
            "two"
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
          ],
          "mode": [
            "two"
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
          ],
          "mode": [
            "two"
          ]
        }
      },
      {
        "key": "firstGroup",
        "label": "Group A value",
        "ko": "A 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ],
          "mode": [
            "two"
          ]
        }
      },
      {
        "key": "secondGroup",
        "label": "Group B value",
        "ko": "B 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ],
          "mode": [
            "two"
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
    "id": "levene",
    "label": "Levene / Brown–Forsythe",
    "ko": "Levene / Brown–Forsythe",
    "input": "groups",
    "suffix": "",
    "example": "levene([1,2,4,5],[2,3,5,8])",
    "help": "Separate group lists; median-centered equal-variance test.",
    "helpKo": "그룹별 목록; 중앙값 기준 등분산 검정.",
    "section": "tests",
    "group": "Distribution & variance",
    "groupKo": "분포·분산 검정",
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
    "section": "tests",
    "group": "Distribution & variance",
    "groupKo": "분포·분산 검정",
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
    "id": "tukey",
    "label": "Tukey–Kramer",
    "ko": "Tukey–Kramer",
    "input": "groups",
    "suffix": "",
    "example": "tukey([1,2,4,5],[2,3,5,8])",
    "help": "All pairwise mean comparisons for independent groups with equal variances.",
    "helpKo": "등분산 독립 그룹의 모든 쌍별 평균 사후비교.",
    "section": "tests",
    "group": "Post-hoc comparisons",
    "groupKo": "사후비교",
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
    "formHelp": "Select independent group columns or group/value columns. Tukey–Kramer compares all pairs assuming equal variances and reports adjusted p values and 95% simultaneous intervals.",
    "formHelpKo": "독립 그룹 열 또는 그룹·값 열을 선택합니다. Tukey–Kramer는 등분산을 가정하여 모든 쌍의 보정 p값·95% 동시 구간을 제공합니다.",
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
    "id": "gameshowell",
    "label": "Games–Howell",
    "ko": "Games–Howell",
    "input": "groups",
    "suffix": "",
    "example": "gameshowell([1,2,4,5],[2,3,5,8])",
    "help": "All pairwise mean comparisons for independent groups with unequal variances.",
    "helpKo": "이분산 독립 그룹의 모든 쌍별 평균 사후비교.",
    "section": "tests",
    "group": "Post-hoc comparisons",
    "groupKo": "사후비교",
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
    "formHelp": "Select independent group columns or group/value columns. Games–Howell compares all pairs allowing unequal variances and reports adjusted p values and 95% simultaneous intervals.",
    "formHelpKo": "독립 그룹 열 또는 그룹·값 열을 선택합니다. Games–Howell은 이분산을 허용하여 모든 쌍의 보정 p값·95% 동시 구간을 제공합니다.",
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
    "id": "twowayanova",
    "label": "Two-way ANOVA",
    "ko": "이요인 ANOVA",
    "input": "table",
    "suffix": ",1",
    "example": "twowayanova([[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]],1)",
    "help": "Independent observations, two categorical factors and a numeric response. Type III F tests with sum contrasts; interaction 1 (default) or additive 0. Normal errors and common residual variance; replication and a full-rank design are required.",
    "helpKo": "독립 관측·두 범주 요인·숫자 반응. 합이 0인 대비의 Type III F 검정. 상호작용 1(기본) 또는 가법 0. 정규 오차·공통 잔차분산을 가정하며 반복 관측과 식별 가능한 설계가 필요합니다.",
    "section": "tests",
    "group": "Group comparisons",
    "groupKo": "그룹 비교",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 구조",
        "type": "choice",
        "default": "groups",
        "choices": [
          {
            "id": "groups",
            "label": "Factor columns + response",
            "ko": "요인 열 + 반응 열"
          },
          {
            "id": "columns",
            "label": "One column per factor cell",
            "ko": "요인 조합별 열"
          }
        ]
      },
      {
        "key": "factorA",
        "label": "Factor A",
        "ko": "요인 A 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "factorB",
        "label": "Factor B",
        "ko": "요인 B 열",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "response",
        "label": "Response",
        "ko": "반응 열",
        "type": "column",
        "default": 2,
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "columns",
        "label": "Cell columns (A slowest)",
        "ko": "조합별 열 (A 최외곽)",
        "type": "columns",
        "default": "auto",
        "when": {
          "layout": [
            "columns"
          ]
        }
      },
      {
        "key": "levelsB",
        "label": "Factor B levels",
        "ko": "요인 B 수준 수",
        "type": "number",
        "default": "2",
        "when": {
          "layout": [
            "columns"
          ]
        }
      },
      {
        "key": "interaction",
        "label": "Model",
        "ko": "모형",
        "type": "choice",
        "default": "1",
        "choices": [
          {
            "id": "1",
            "label": "Main effects + interaction",
            "ko": "주효과 + 상호작용"
          },
          {
            "id": "0",
            "label": "Additive main effects",
            "ko": "가법 주효과"
          }
        ]
      }
    ],
    "formHelp": "Select two categorical factor columns and the numeric response, or columns for every factor cell ordered with A slowest. Observations must be independent; repeated measures belong in Repeated-measures ANOVA. Type III sum contrasts handle unequal cell sizes. Replication and identifiable cells are required for the interaction model. Results include residual diagnostics and cell mean intervals.",
    "formHelpKo": "두 범주 요인 열·숫자 반응 열 또는 A를 최외곽으로 정렬한 요인 조합별 열을 선택합니다. 관측은 독립이어야 하며 반복측정은 반복측정 ANOVA를 사용하세요. 합이 0인 대비의 Type III 검정으로 불균형 셀 크기를 처리합니다. 상호작용 모형에는 반복 관측·식별 가능한 셀이 필요합니다. 잔차 진단과 셀 평균 구간을 함께 제공합니다.",
    "exampleRows": [
      [
        "1",
        "1",
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
        "5"
      ],
      [
        "1",
        "2",
        "6"
      ],
      [
        "2",
        "1",
        "4"
      ],
      [
        "2",
        "1",
        "5"
      ],
      [
        "2",
        "2",
        "8"
      ],
      [
        "2",
        "2",
        "10"
      ]
    ]
  },
  {
    "id": "ancova",
    "label": "ANCOVA",
    "ko": "ANCOVA (공분산분석)",
    "input": "table",
    "suffix": ",0.95,1",
    "example": "ancova([[1,1,3],[1,2,5],[1,3,4],[1,4,8],[2,2,6],[2,3,7],[2,4,9],[2,5,8],[3,1,5],[3,3,8],[3,4,10],[3,6,11]],0.95,1)",
    "help": "Rows: numeric group ID, one or more covariates, response; confidence level (default .95); slope homogeneity check 0/1 (default 1). One factor, common slopes, Type II F tests and adjusted means at pooled covariate means.",
    "helpKo": "열: 숫자 그룹 ID, 하나 이상의 공변량, 종속변수; 신뢰수준(기본 .95), 기울기 동질성 검정 0/1(기본 1). 일요인·공통 기울기, Type II F 검정, 전체 공변량 평균에서의 조정 평균.",
    "section": "tests",
    "group": "Group comparisons",
    "groupKo": "그룹 비교",
    "controls": [
      {
        "key": "group",
        "label": "Group column",
        "ko": "그룹 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "response",
        "label": "Response",
        "ko": "종속변수 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Covariates",
        "ko": "공변량 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "level",
        "label": "Confidence level",
        "ko": "신뢰수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "slopes",
        "label": "Slope homogeneity",
        "ko": "회귀 기울기 동질성",
        "type": "choice",
        "default": "test",
        "choices": [
          {
            "id": "test",
            "label": "Test",
            "ko": "검정"
          },
          {
            "id": "none",
            "label": "Skip",
            "ko": "생략"
          }
        ]
      }
    ],
    "formHelp": "Compare groups after adjusting for selected covariates. Text group labels are accepted. Type II tests, adjusted means, and optional slope homogeneity check.",
    "formHelpKo": "선택한 공변량을 보정하여 그룹을 비교합니다. 문자 그룹도 사용할 수 있습니다. Type II 검정·조정 평균·선택적 기울기 동질성 검정.",
    "exampleRows": [
      [
        "1",
        "1",
        "3"
      ],
      [
        "1",
        "2",
        "5"
      ],
      [
        "1",
        "3",
        "4"
      ],
      [
        "1",
        "4",
        "8"
      ],
      [
        "2",
        "2",
        "6"
      ],
      [
        "2",
        "3",
        "7"
      ],
      [
        "2",
        "4",
        "9"
      ],
      [
        "2",
        "5",
        "8"
      ],
      [
        "3",
        "1",
        "5"
      ],
      [
        "3",
        "3",
        "8"
      ],
      [
        "3",
        "4",
        "10"
      ],
      [
        "3",
        "6",
        "11"
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
    "section": "tests",
    "group": "Group comparisons",
    "groupKo": "그룹 비교",
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
      },
      {
        "key": "matching",
        "label": "Pair matching",
        "ko": "대응 연결",
        "type": "choice",
        "default": "order",
        "choices": [
          {
            "id": "order",
            "label": "Within-group row order",
            "ko": "그룹 안의 행 순서"
          },
          {
            "id": "subject",
            "label": "Subject ID",
            "ko": "대상 ID"
          }
        ],
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "subject",
        "label": "Subject ID",
        "ko": "대상 ID 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "groups"
          ],
          "matching": [
            "subject"
          ]
        }
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
    "id": "friedman",
    "label": "Friedman test",
    "ko": "Friedman 검정",
    "input": "table",
    "suffix": "",
    "example": "friedman([[2,4,5],[3,4,7],[4,7,8],[2,3,6],[5,6,7]])",
    "help": "Rows are independent subjects; columns are at least three repeated conditions. Complete matched rows, midranks and tie correction. Chi-square approximation; small samples or few conditions can give inaccurate p values. Reports Kendall W.",
    "helpKo": "행은 독립 대상, 열은 3개 이상 반복 조건입니다. 완전한 대응 행·평균순위·동점 보정. χ² 근사이므로 소표본·소수 조건에서 p값이 부정확할 수 있습니다. Kendall W를 제공합니다.",
    "section": "tests",
    "group": "Group comparisons",
    "groupKo": "그룹 비교",
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
      },
      {
        "key": "matching",
        "label": "Pair matching",
        "ko": "대응 연결",
        "type": "choice",
        "default": "order",
        "choices": [
          {
            "id": "order",
            "label": "Within-group row order",
            "ko": "그룹 안의 행 순서"
          },
          {
            "id": "subject",
            "label": "Subject ID",
            "ko": "대상 ID"
          }
        ],
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "subject",
        "label": "Subject ID",
        "ko": "대상 ID 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "groups"
          ],
          "matching": [
            "subject"
          ]
        }
      }
    ],
    "formHelp": "Choose at least three numeric condition columns. Each row must be the same subject across conditions; incomplete selected rows are rejected. Tie-corrected chi-square approximation; small-sample p values can be inaccurate.",
    "formHelpKo": "숫자 반복 조건 열을 3개 이상 선택합니다. 각 행은 모든 조건에서 같은 대상이어야 하며 선택 열의 불완전한 행은 거부합니다. 동점 보정 χ² 근사이며 소표본 p값은 부정확할 수 있습니다.",
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
    "id": "cohend",
    "label": "Cohen’s d",
    "ko": "Cohen의 d",
    "input": "groups",
    "suffix": ",independent",
    "example": "cohend([1,2,4,5],[2,3,5,8],independent)",
    "help": "Two samples; independent (pooled d) or paired (dz).",
    "helpKo": "두 표본; independent(합동 SD) 또는 paired(차이의 SD).",
    "section": "tests",
    "group": "Effect sizes & multiple testing",
    "groupKo": "효과크기·다중검정",
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
        "key": "first",
        "label": "Group A column",
        "ko": "A 그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "columns"
          ]
        }
      },
      {
        "key": "second",
        "label": "Group B column",
        "ko": "B 그룹 열",
        "type": "column",
        "default": 1,
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
      },
      {
        "key": "firstGroup",
        "label": "Group A value",
        "ko": "A 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "secondGroup",
        "label": "Group B value",
        "ko": "B 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "design",
        "label": "Comparison",
        "ko": "비교 방식",
        "type": "choice",
        "default": "independent",
        "choices": [
          {
            "id": "independent",
            "label": "Independent groups",
            "ko": "독립 표본"
          },
          {
            "id": "paired",
            "label": "Paired observations",
            "ko": "대응 표본"
          }
        ]
      },
      {
        "key": "matching",
        "label": "Pair matching",
        "ko": "대응 연결",
        "type": "choice",
        "default": "order",
        "choices": [
          {
            "id": "order",
            "label": "Within-group row order",
            "ko": "그룹 안의 행 순서"
          },
          {
            "id": "subject",
            "label": "Subject ID",
            "ko": "대상 ID"
          }
        ],
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "subject",
        "label": "Subject ID",
        "ko": "대상 ID 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "groups"
          ],
          "matching": [
            "subject"
          ]
        }
      }
    ],
    "formHelp": "Select two columns and independent or paired comparison. Paired analysis uses complete rows; independent samples omit blank cells separately.",
    "formHelpKo": "두 열과 독립·대응 비교를 선택합니다. 대응 분석은 완전한 행을, 독립 분석은 각 열의 빈 셀을 별도로 제외합니다.",
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
    "id": "eta2",
    "label": "η² effect size",
    "ko": "η² 효과크기",
    "input": "groups",
    "suffix": "",
    "example": "eta2([1,2,4,5],[2,3,5,8])",
    "help": "Independent groups as separate lists.",
    "helpKo": "독립 그룹별 목록.",
    "section": "tests",
    "group": "Effect sizes & multiple testing",
    "groupKo": "효과크기·다중검정",
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
    "formHelp": "Compare two or more groups. Choose group columns or a group/value layout.",
    "formHelpKo": "두 개 이상 그룹을 비교합니다. 열별 그룹 또는 그룹·값 열을 선택하세요.",
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
    "id": "padjust",
    "label": "Multiple testing",
    "ko": "다중검정 보정",
    "input": "list",
    "suffix": ",holm,0.05",
    "example": "padjust([0.01,0.04,0.03,0.2],holm,0.05)",
    "help": "p values; method bonferroni / holm / fdr (BH) / by; alpha.",
    "helpKo": "p값 목록; 방법 bonferroni / holm / fdr (BH) / by; 유의수준.",
    "section": "tests",
    "group": "Effect sizes & multiple testing",
    "groupKo": "효과크기·다중검정",
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
    "id": "linearmodel",
    "label": "Factorial linear model / ANOVA",
    "ko": "요인 선형회귀 / ANOVA",
    "input": "table",
    "suffix": ",[1,2],2,3,sum",
    "example": "linearmodel([[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]],[1,2],2,3,sum)",
    "help": "General OLS with numeric and categorical predictors. Categorical positions are one-based; interaction order, Type II/III, sum/treatment coding. Uses joint partial F tests for each term. Three-way and higher interactions require enough replicated observations and identifiable columns.",
    "helpKo": "숫자·범주 설명변수를 포함한 일반 OLS. 범주 변수 번호는 1부터 시작하며 상호작용 차수·Type II/III·sum/treatment 코딩을 지정합니다. 항마다 여러 계수를 부분 F로 함께 검정합니다. 3요인 이상 상호작용에도 충분한 반복 관측·식별 가능한 열이 필요합니다.",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
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
        "key": "categorical",
        "label": "Categorical predictors",
        "ko": "범주 설명변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "order",
        "label": "Interaction order (1 = additive)",
        "ko": "상호작용 차수 (1 = 가법)",
        "type": "number",
        "default": "2"
      },
      {
        "key": "ssType",
        "label": "Sums of squares",
        "ko": "제곱합",
        "type": "choice",
        "default": "3",
        "choices": [
          {
            "id": "3",
            "label": "Type III",
            "ko": "Type III"
          },
          {
            "id": "2",
            "label": "Type II",
            "ko": "Type II"
          }
        ]
      },
      {
        "key": "coding",
        "label": "Categorical coding",
        "ko": "범주 코딩",
        "type": "choice",
        "default": "sum",
        "choices": [
          {
            "id": "sum",
            "label": "Sum-to-zero contrasts",
            "ko": "합이 0인 대비"
          },
          {
            "id": "treatment",
            "label": "Treatment / dummy coding",
            "ko": "처리 / 더미 코딩"
          }
        ]
      }
    ],
    "formHelp": "Choose response and predictors; mark only the categorical predictors. Numeric predictors retain their units. Interaction order 1 is additive, 2 includes all pairs, 3 all triples, etc. Choose Type II or III and sum or dummy coding; Type III factorial interactions require sum contrasts. Coefficient intervals and joint term F tests share the same OLS fit. Numeric main effects with interactions refer to zero: center covariates when appropriate.",
    "formHelpKo": "반응·설명변수를 선택하고 그중 범주 변수만 표시합니다. 숫자 변수는 원래 단위를 사용합니다. 차수 1은 가법, 2는 모든 쌍, 3은 모든 삼중 상호작용 등을 포함합니다. Type II/III·합 대비/더미 코딩을 선택하며 Type III 요인 상호작용에는 합 대비가 필요합니다. 계수 구간·항별 부분 F 검정은 같은 OLS 적합을 사용합니다. 숫자 상호작용의 주효과는 0 기준이므로 필요하면 공변량을 중심화하세요.",
    "exampleRows": [
      [
        "1",
        "1",
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
        "5"
      ],
      [
        "1",
        "2",
        "6"
      ],
      [
        "2",
        "1",
        "4"
      ],
      [
        "2",
        "1",
        "5"
      ],
      [
        "2",
        "2",
        "8"
      ],
      [
        "2",
        "2",
        "10"
      ]
    ]
  },
  {
    "id": "glm",
    "label": "Generalized linear model (GLM)",
    "ko": "GLM (일반화 선형모형)",
    "input": "table",
    "suffix": ",gaussian,auto,1",
    "example": "glm([[0,2],[1,4],[2,4],[3,7],[4,8],[5,9]],gaussian,auto,1)",
    "help": "Rows: predictors, response; family gaussian / binomial (0/1) / poisson / gamma / inversegaussian / nbinom; link auto or a supported link; NB2 alpha: positive fixed value (default 1) or estimate for joint ML; optional offset/exposure vector and mode. Default links: identity, logit, log, log, log, log. Model-based Wald z 95% intervals; Pearson dispersion for Gaussian/Gamma/inverse Gaussian. Use estimate as the fourth argument to estimate NB2 alpha jointly with coefficients; its uncertainty enters the observed-information covariance. Numeric alpha retains the fixed-alpha model.",
    "helpKo": "열: 설명변수, 반응변수; 분포 gaussian·binomial(0/1)·poisson·gamma·inversegaussian·nbinom; 연결함수 auto 또는 지원 함수; NB2 alpha: 양수 고정값(기본 1) 또는 estimate(공동 ML 추정); 선택적 오프셋·노출량 목록과 유형. 기본 연결함수는 identity·logit·log·log·log·log. 모형 기반 Wald z 95% 구간; 정규·Gamma·역가우스는 Pearson 분산 추정. 넷째 인수를 estimate로 지정하면 NB2 alpha와 계수를 공동 ML 추정하며 관측 정보행렬에 alpha의 불확실성을 반영합니다. 숫자를 입력하면 alpha를 고정합니다.",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
      {
        "key": "response",
        "label": "Response",
        "ko": "반응변수 열",
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
            "ko": "정규"
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
          },
          {
            "id": "gamma",
            "label": "Gamma",
            "ko": "Gamma"
          },
          {
            "id": "inversegaussian",
            "label": "Inverse Gaussian",
            "ko": "역가우스"
          },
          {
            "id": "nbinom",
            "label": "Negative binomial (NB2)",
            "ko": "음이항 (NB2)"
          }
        ]
      },
      {
        "key": "link",
        "label": "Link function",
        "ko": "연결함수",
        "type": "choice",
        "default": "auto",
        "choices": [
          {
            "id": "auto",
            "label": "Default for family",
            "ko": "분포별 기본값"
          },
          {
            "id": "identity",
            "label": "Identity",
            "ko": "항등",
            "when": {
              "family": [
                "gaussian"
              ]
            }
          },
          {
            "id": "log",
            "label": "Log",
            "ko": "로그",
            "when": {
              "family": [
                "gaussian",
                "poisson",
                "gamma",
                "inversegaussian",
                "nbinom"
              ]
            }
          },
          {
            "id": "logit",
            "label": "Logit",
            "ko": "로짓",
            "when": {
              "family": [
                "binomial"
              ]
            }
          },
          {
            "id": "probit",
            "label": "Probit",
            "ko": "프로빗",
            "when": {
              "family": [
                "binomial"
              ]
            }
          },
          {
            "id": "cloglog",
            "label": "Complementary log-log",
            "ko": "상보 로그로그",
            "when": {
              "family": [
                "binomial"
              ]
            }
          },
          {
            "id": "inverse",
            "label": "Inverse",
            "ko": "역수",
            "when": {
              "family": [
                "gamma"
              ]
            }
          },
          {
            "id": "inverse_squared",
            "label": "Inverse squared",
            "ko": "역수 제곱",
            "when": {
              "family": [
                "inversegaussian"
              ]
            }
          }
        ]
      },
      {
        "key": "dispersionMode",
        "label": "NB2 dispersion",
        "ko": "NB2 과산포",
        "type": "choice",
        "default": "fixed",
        "choices": [
          {
            "id": "fixed",
            "label": "Fixed alpha",
            "ko": "alpha 고정"
          },
          {
            "id": "estimate",
            "label": "Estimate by ML",
            "ko": "ML 추정"
          }
        ],
        "when": {
          "family": [
            "nbinom"
          ]
        }
      },
      {
        "key": "alpha",
        "label": "NB2 alpha (fixed)",
        "ko": "NB2 alpha (고정)",
        "type": "number",
        "default": "1",
        "when": {
          "family": [
            "nbinom"
          ],
          "dispersionMode": [
            "fixed"
          ]
        }
      },
      {
        "key": "adjustment",
        "label": "Offset / exposure",
        "ko": "오프셋·노출량",
        "type": "choice",
        "default": "none",
        "choices": [
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          },
          {
            "id": "offset",
            "label": "Log offset",
            "ko": "로그 오프셋"
          },
          {
            "id": "exposure",
            "label": "Exposure",
            "ko": "노출량"
          }
        ]
      },
      {
        "key": "offset",
        "label": "Offset / exposure column",
        "ko": "오프셋·노출량 열",
        "type": "column",
        "default": 0,
        "when": {
          "adjustment": [
            "offset",
            "exposure"
          ]
        }
      }
    ],
    "formHelp": "Choose a family, its link, response and predictors. Binomial uses 0/1; counts use nonnegative integers; Gamma/inverse Gaussian use positive responses. Exposure requires a log link. NB2 dispersion: fixed alpha or joint ML estimation.",
    "formHelpKo": "분포·연결함수·반응변수·설명변수를 선택하세요. 이항은 0/1, 빈도는 음이 아닌 정수, Gamma·역가우스는 양수입니다. 노출량은 로그 연결에서만 사용합니다. NB2 과산포는 alpha 고정 또는 공동 ML 추정을 선택합니다.",
    "exampleRows": [
      [
        "0",
        "2"
      ],
      [
        "1",
        "4"
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
        "9"
      ]
    ]
  },
  {
    "id": "poissonreg",
    "label": "Poisson regression",
    "ko": "포아송 회귀",
    "input": "table",
    "suffix": "",
    "example": "poissonreg([[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]])",
    "help": "Rows: predictors, integer count response. Log link. Optional second argument row-aligned offset/exposure list; third argument offset (default) or exposure (positive, log transformed).",
    "helpKo": "열: 설명변수, 정수 빈도 반응. 로그 연결함수. 선택적 둘째 인수 행별 오프셋·노출량 목록; 셋째 인수 offset(기본)·exposure(양수, 로그 변환).",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
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
        "key": "adjustment",
        "label": "Offset / exposure",
        "ko": "오프셋·노출량",
        "type": "choice",
        "default": "none",
        "choices": [
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          },
          {
            "id": "offset",
            "label": "Log offset",
            "ko": "로그 오프셋"
          },
          {
            "id": "exposure",
            "label": "Exposure",
            "ko": "노출량"
          }
        ]
      },
      {
        "key": "offset",
        "label": "Offset / exposure column",
        "ko": "오프셋·노출량 열",
        "type": "column",
        "default": 0,
        "when": {
          "adjustment": [
            "offset",
            "exposure"
          ]
        }
      }
    ],
    "formHelp": "Log-link counts; choose predictors, response and optional offset / positive exposure.",
    "formHelpKo": "로그 연결 빈도 모형; 설명변수·반응·선택적 오프셋·양수 노출량.",
    "exampleRows": [
      [
        "0",
        "1"
      ],
      [
        "0",
        "0"
      ],
      [
        "1",
        "3"
      ],
      [
        "1",
        "1"
      ],
      [
        "2",
        "2"
      ],
      [
        "2",
        "5"
      ],
      [
        "3",
        "4"
      ],
      [
        "3",
        "8"
      ],
      [
        "4",
        "6"
      ],
      [
        "4",
        "10"
      ]
    ]
  },
  {
    "id": "nbreg",
    "label": "Negative binomial regression",
    "ko": "음이항 회귀",
    "input": "table",
    "suffix": "",
    "example": "nbreg([[0,0],[0,0],[0,1],[0,8],[1,0],[1,1],[1,3],[1,15],[2,0],[2,2],[2,5],[2,23],[3,1],[3,3],[3,10],[3,35]])",
    "help": "Rows: predictors, integer count response. NB2 with estimated dispersion. Optional offset/exposure list and offset (default) / exposure mode.",
    "helpKo": "열: 설명변수, 정수 빈도 반응. NB2 과산포 모수 추정. 선택적 오프셋·노출량 목록과 offset(기본)·exposure 모드.",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
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
        "key": "adjustment",
        "label": "Offset / exposure",
        "ko": "오프셋·노출량",
        "type": "choice",
        "default": "none",
        "choices": [
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          },
          {
            "id": "offset",
            "label": "Log offset",
            "ko": "로그 오프셋"
          },
          {
            "id": "exposure",
            "label": "Exposure",
            "ko": "노출량"
          }
        ]
      },
      {
        "key": "offset",
        "label": "Offset / exposure column",
        "ko": "오프셋·노출량 열",
        "type": "column",
        "default": 0,
        "when": {
          "adjustment": [
            "offset",
            "exposure"
          ]
        }
      }
    ],
    "formHelp": "NB2 counts with estimated dispersion; optional offset / positive exposure.",
    "formHelpKo": "과산포를 추정하는 NB2 빈도 모형; 선택적 오프셋·양수 노출량.",
    "exampleRows": [
      [
        "0",
        "0"
      ],
      [
        "0",
        "0"
      ],
      [
        "0",
        "1"
      ],
      [
        "0",
        "8"
      ],
      [
        "1",
        "0"
      ],
      [
        "1",
        "1"
      ],
      [
        "1",
        "3"
      ],
      [
        "1",
        "15"
      ],
      [
        "2",
        "0"
      ],
      [
        "2",
        "2"
      ],
      [
        "2",
        "5"
      ],
      [
        "2",
        "23"
      ],
      [
        "3",
        "1"
      ],
      [
        "3",
        "3"
      ],
      [
        "3",
        "10"
      ],
      [
        "3",
        "35"
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
    "helpKo": "열: 설명변수, 숫자 범주 반응. 가장 작은 범주가 기준.",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
      {
        "key": "response",
        "label": "Category response",
        "ko": "범주 반응 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Predictors",
        "ko": "설명변수 열",
        "type": "columns",
        "default": "auto"
      }
    ],
    "formHelp": "Choose numeric category response and predictors. Smallest category is the reference.",
    "formHelpKo": "숫자 범주 반응 열과 설명변수를 선택합니다. 가장 작은 범주가 기준입니다.",
    "exampleRows": [
      [
        "-2",
        "0"
      ],
      [
        "-2",
        "1"
      ],
      [
        "-1",
        "0"
      ],
      [
        "-1",
        "2"
      ],
      [
        "0",
        "0"
      ],
      [
        "0",
        "1"
      ],
      [
        "0",
        "2"
      ],
      [
        "1",
        "1"
      ],
      [
        "1",
        "2"
      ],
      [
        "2",
        "1"
      ],
      [
        "2",
        "2"
      ],
      [
        "2",
        "0"
      ]
    ]
  },
  {
    "id": "ordinal",
    "label": "Ordinal logistic",
    "ko": "순서형 로지스틱",
    "input": "table",
    "suffix": "",
    "example": "ordinal([[-2,0],[-2,1],[-1,0],[-1,2],[0,0],[0,1],[0,2],[1,1],[1,2],[2,1],[2,2],[2,0]])",
    "help": "Rows: predictors, ordered numeric response. Proportional-odds cumulative logit.",
    "helpKo": "열: 설명변수, 순서가 있는 숫자 반응. 비례오즈 누적 로짓.",
    "section": "models",
    "group": "Generalized regression",
    "groupKo": "일반화 회귀",
    "controls": [
      {
        "key": "response",
        "label": "Ordered category response",
        "ko": "순서형 반응 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Predictors",
        "ko": "설명변수 열",
        "type": "columns",
        "default": "auto"
      }
    ],
    "formHelp": "Choose ordered numeric category response and predictors. Category order follows numeric order; proportional odds are assumed.",
    "formHelpKo": "순서가 있는 숫자 범주 반응 열과 설명변수를 선택합니다. 숫자 순서를 사용하며 비례오즈를 가정합니다.",
    "exampleRows": [
      [
        "-2",
        "0"
      ],
      [
        "-2",
        "1"
      ],
      [
        "-1",
        "0"
      ],
      [
        "-1",
        "2"
      ],
      [
        "0",
        "0"
      ],
      [
        "0",
        "1"
      ],
      [
        "0",
        "2"
      ],
      [
        "1",
        "1"
      ],
      [
        "1",
        "2"
      ],
      [
        "2",
        "1"
      ],
      [
        "2",
        "2"
      ],
      [
        "2",
        "0"
      ]
    ]
  },
  {
    "id": "mixedmodel",
    "label": "Mixed model",
    "ko": "혼합모형",
    "input": "table",
    "suffix": ",0,reml",
    "example": "mixedmodel([[1,0,2],[1,1,4],[1,2,4],[2,0,3],[2,1,4],[2,2,6],[3,0,1],[3,1,3],[3,2,4],[4,0,4],[4,1,5],[4,2,8]],0,reml)",
    "help": "Rows: subject ID, predictors, response. Gaussian random intercept with up to three random slopes (0 none, a predictor position, or [1,2]); third argument reml (default) or ml; up to 5000 rows. Includes subject BLUPs, slope correlations and singular-fit diagnostics; random-slope ICC is at x=0; asymptotic Wald z inference. Optional fourth argument: profile (ML fixed-effect profile CI) or [bootstrap,200,0] (parametric fixed-effect percentile CI); alternative CI omit Wald p-values. Reports logLik/AIC/BIC; compare REML criteria only with identical fixed effects and data. Nonconverged fits withhold Wald inference.",
    "helpKo": "열: 대상 ID, 설명변수, 반응. Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기(0 없음, 변수 위치, 또는 [1,2]); 셋째 인수 reml(기본)·ml; 최대 5000행. 대상별 BLUP·기울기 상관·singular 진단 포함; 기울기 ICC는 x=0 기준; 점근 Wald z 추론. 선택적 넷째 인수: profile(ML 고정효과 프로파일 구간), [bootstrap,200,0](모수적 고정효과 백분위 구간); 대안 구간은 Wald p값을 생략합니다. logLik/AIC/BIC 제공; REML 비교는 같은 고정효과·자료에서만 가능합니다. 미수렴 적합의 Wald 추론은 표시하지 않습니다.",
    "section": "models",
    "group": "Repeated & clustered data",
    "groupKo": "반복·군집 자료",
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
        "default": "reml",
        "choices": [
          {
            "id": "reml",
            "label": "REML",
            "ko": "REML"
          },
          {
            "id": "ml",
            "label": "ML",
            "ko": "ML"
          }
        ]
      },
      {
        "key": "ci",
        "label": "Fixed-effect 95% CI",
        "ko": "고정효과 95% 신뢰구간",
        "type": "choice",
        "default": "wald",
        "choices": [
          {
            "id": "wald",
            "label": "Wald",
            "ko": "Wald"
          },
          {
            "id": "profile",
            "label": "ML profile likelihood",
            "ko": "ML 프로파일 우도"
          },
          {
            "id": "bootstrap",
            "label": "Parametric bootstrap",
            "ko": "모수적 부트스트랩"
          }
        ]
      },
      {
        "key": "ciSamples",
        "label": "Bootstrap refits",
        "ko": "부트스트랩 재적합 수",
        "type": "number",
        "default": "200",
        "when": {
          "ci": [
            "bootstrap"
          ]
        }
      },
      {
        "key": "ciSeed",
        "label": "Bootstrap seed",
        "ko": "부트스트랩 시드",
        "type": "number",
        "default": "0",
        "when": {
          "ci": [
            "bootstrap"
          ]
        }
      }
    ],
    "formHelp": "Gaussian random intercept + up to three random slopes; REML (default) / ML, singular-fit diagnostics and subject BLUPs. ICC for random slopes is at x=0. Slopes: 0, a position or 1,2. Wald z inference. Optional fourth argument: profile (ML fixed-effect profile CI) or [bootstrap,200,0] (parametric fixed-effect percentile CI); alternative CI omit Wald p-values. Reports logLik/AIC/BIC; compare REML criteria only with identical fixed effects and data. Nonconverged fits withhold Wald inference.",
    "formHelpKo": "Gaussian 랜덤 절편 + 최대 3개 랜덤 기울기; REML(기본)·ML, singular 진단·대상별 BLUP. 기울기 모형의 ICC는 x=0 기준. 기울기: 0, 번호 또는 1,2. Wald z 추론. 선택적 넷째 인수: profile(ML 고정효과 프로파일 구간), [bootstrap,200,0](모수적 고정효과 백분위 구간); 대안 구간은 Wald p값을 생략합니다. logLik/AIC/BIC 제공; REML 비교는 같은 고정효과·자료에서만 가능합니다. 미수렴 적합의 Wald 추론은 표시하지 않습니다.",
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
    "id": "glmm",
    "label": "Generalized mixed model (GLMM)",
    "ko": "일반화 혼합모형 (GLMM)",
    "input": "table",
    "suffix": ",binomial,15",
    "example": "glmm([[1,0,0],[1,1,0],[1,2,1],[2,0,0],[2,1,1],[2,2,1],[3,0,0],[3,1,0],[3,2,0],[4,0,1],[4,1,1],[4,2,1],[5,0,1],[5,1,0],[5,2,1],[6,0,0],[6,1,1],[6,2,0]],binomial,15)",
    "help": "Rows: subject ID, predictors, response. Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), without quadrature refit; use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial (0/1, logit), poisson or nbinom (NB2, log). ML adaptive Gauss-Hermite quadrature: 15 points default, 1 = Laplace, otherwise 7-31. Optional fourth argument offset vector, fifth offset / exposure. Limit 1500 rows, 8 fixed coefficients. Subject-specific effects; joint marginal observed information by central differences; asymptotic Wald inference. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.",
    "helpKo": "열: 대상 ID, 설명변수, 반응. 랜덤 절편 + 선택적 상관 랜덤 기울기 1개(일곱째 인수: 선택한 설명변수 번호, 0 없음). 기울기는 2차원 Laplace(셋째 인수 1), 적분점 재적합 미지원; 번호 앞에 [],offset,likelihood를 지정합니다. 공분산·조건부 최빈값은 원래 단위로 표시합니다. 랜덤 절편: binomial(0/1, 로짓), poisson·nbinom(NB2, 로그). ML 적응형 Gauss-Hermite 적분: 기본 15점, 1=Laplace, 그 외 7~31점. 선택적 넷째 인수 오프셋 목록, 다섯째 offset·exposure. 최대 1500행·고정계수 8개. 대상별 조건부 효과, 중앙차분 관측 정보행렬·점근 Wald 추론. 소수 대상의 Wald 추론은 부정확할 수 있습니다. Laplace·31점도 다른 적분점의 우도를 비교합니다. 선택적 여섯째 인수 refit은 재적합 계수를 비교하고 0.1 SE 초과 변동이면 CI·p값을 생략합니다. 오프셋이 없으면 refit 앞에 [],offset을 사용합니다.",
    "section": "models",
    "group": "Repeated & clustered data",
    "groupKo": "반복·군집 자료",
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
        "label": "Random slope position (0 = none, Laplace)",
        "ko": "랜덤 기울기 번호 (0 = 없음, Laplace)",
        "type": "number",
        "default": "0"
      },
      {
        "key": "family",
        "label": "Family",
        "ko": "분포",
        "type": "choice",
        "default": "binomial",
        "choices": [
          {
            "id": "binomial",
            "label": "Binomial (0/1)",
            "ko": "이항 (0/1)"
          },
          {
            "id": "poisson",
            "label": "Poisson",
            "ko": "포아송"
          },
          {
            "id": "nbinom",
            "label": "Negative binomial (NB2)",
            "ko": "음이항 (NB2)"
          }
        ]
      },
      {
        "key": "points",
        "label": "Quadrature points (1 = Laplace)",
        "ko": "적분 점 수 (1 = Laplace)",
        "type": "number",
        "default": "15",
        "when": {
          "slope": [
            "0"
          ]
        }
      },
      {
        "key": "sensitivity",
        "label": "Quadrature sensitivity",
        "ko": "적분 민감도 확인",
        "type": "choice",
        "default": "likelihood",
        "choices": [
          {
            "id": "likelihood",
            "label": "Compare likelihood",
            "ko": "우도 비교"
          },
          {
            "id": "refit",
            "label": "Refit and compare coefficients",
            "ko": "재적합·계수 비교"
          }
        ],
        "when": {
          "slope": [
            "0"
          ]
        }
      },
      {
        "key": "adjustment",
        "label": "Offset / exposure",
        "ko": "오프셋·노출량",
        "type": "choice",
        "default": "none",
        "choices": [
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          },
          {
            "id": "offset",
            "label": "Log offset",
            "ko": "로그 오프셋"
          },
          {
            "id": "exposure",
            "label": "Exposure",
            "ko": "노출량"
          }
        ],
        "when": {
          "family": [
            "poisson",
            "nbinom"
          ]
        }
      },
      {
        "key": "offset",
        "label": "Offset / exposure column",
        "ko": "오프셋·노출량 열",
        "type": "column",
        "default": 0,
        "when": {
          "adjustment": [
            "offset",
            "exposure"
          ],
          "family": [
            "poisson",
            "nbinom"
          ]
        }
      }
    ],
    "formHelp": "Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), without quadrature refit; use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial / Poisson / NB2. ML quadrature (15 default, 1 Laplace, 7-31); conditional effects. Count families support log offset or positive exposure. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.",
    "formHelpKo": "랜덤 절편 또는 절편 + 상관 기울기 1개. 선택적 일곱째 인수: 선택한 설명변수 번호(0 없음). 기울기는 Laplace(셋째 인수 1), 3개 이상 대상 내 설명변수 변화가 필요하며 적분 민감도 확인은 미지원입니다. ICC는 x=0 기준이며 singular·식별 불가 적합은 Wald 추론을 생략합니다. 이항·포아송·NB2. ML 적분(기본 15점, 1 Laplace, 7~31); 조건부 효과. 빈도 분포는 로그 오프셋·양수 노출량 지원. 소수 대상의 Wald 추론은 부정확할 수 있습니다. Laplace·31점도 다른 적분점의 우도를 비교합니다. 선택적 여섯째 인수 refit은 재적합 계수를 비교하고 0.1 SE 초과 변동이면 CI·p값을 생략합니다. 오프셋이 없으면 refit 앞에 [],offset을 사용합니다.",
    "exampleRows": [
      [
        "1",
        "0",
        "0"
      ],
      [
        "1",
        "1",
        "0"
      ],
      [
        "1",
        "2",
        "1"
      ],
      [
        "2",
        "0",
        "0"
      ],
      [
        "2",
        "1",
        "1"
      ],
      [
        "2",
        "2",
        "1"
      ],
      [
        "3",
        "0",
        "0"
      ],
      [
        "3",
        "1",
        "0"
      ],
      [
        "3",
        "2",
        "0"
      ],
      [
        "4",
        "0",
        "1"
      ],
      [
        "4",
        "1",
        "1"
      ],
      [
        "4",
        "2",
        "1"
      ],
      [
        "5",
        "0",
        "1"
      ],
      [
        "5",
        "1",
        "0"
      ],
      [
        "5",
        "2",
        "1"
      ],
      [
        "6",
        "0",
        "0"
      ],
      [
        "6",
        "1",
        "1"
      ],
      [
        "6",
        "2",
        "0"
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
    "help": "Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independence / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE. Pearson dispersion-adjusted correlation; AR(1) uses row order and equal spacing. Few-cluster Wald inference may be unreliable. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. This does not guarantee reliable inference with very few clusters.",
    "helpKo": "열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE. Pearson 분산 보정 상관; AR(1)은 행 순서·등간격 사용. 소수 군집의 Wald 추론은 부정확할 수 있습니다. 선택적 다섯째 인수 small은 Mancl-DeRouen 공분산·군집 수-계수 수 자유도의 t 추론을 적용합니다. 상호작용이 없으면 넷째 인수는 []입니다. 극소수 군집에서의 신뢰성을 보장하지는 않습니다.",
    "section": "models",
    "group": "Repeated & clustered data",
    "groupKo": "반복·군집 자료",
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
        "key": "correction",
        "label": "Covariance correction",
        "ko": "공분산 보정",
        "type": "choice",
        "default": "robust",
        "choices": [
          {
            "id": "robust",
            "label": "Asymptotic sandwich",
            "ko": "점근 샌드위치"
          },
          {
            "id": "small",
            "label": "Mancl-DeRouen + t",
            "ko": "Mancl-DeRouen + t"
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
    "formHelp": "Working correlation independence / exchangeable / AR(1); dispersion-adjusted correlation and cluster-robust SE. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. This does not guarantee reliable inference with very few clusters. AR(1): row order, equal spacing. Interactions accept header names (age,weight), the shown column letters or labels (y,z / age (y),weight (z)), column numbers (2,3) or predictor order (p1,p2); separate pairs with ;.",
    "formHelpKo": "작업상관 independence / exchangeable / AR(1); 분산 보정 상관·군집 강건 표준오차. 선택적 다섯째 인수 small은 Mancl-DeRouen 공분산·군집 수-계수 수 자유도의 t 추론을 적용합니다. 상호작용이 없으면 넷째 인수는 []입니다. 극소수 군집에서의 신뢰성을 보장하지는 않습니다. AR(1): 행 순서·등간격. 상호작용은 열 이름(age,weight), 표시된 열 문자·라벨(y,z / age (y),weight (z)), 열 번호(2,3), 설명변수 순서(p1,p2)로 입력하고 쌍은 ;로 구분합니다.",
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
    "id": "crossvalidate",
    "label": "Cross-validation",
    "ko": "교차검증",
    "input": "table",
    "suffix": ",3,0",
    "example": "crossvalidate([[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]],3,0)",
    "help": "Rows: predictors, response; folds, seed; split random (default) / blocked / stratified; model linear (default) / ridge / lasso / elasticnet / logistic; penalty alpha or [alpha,l1 ratio].",
    "helpKo": "열: 설명변수, 반응; 폴드 수, 시드; 분할 random(기본) / blocked / stratified; 모형 linear(기본) / ridge / lasso / elasticnet / logistic; 벌점 alpha 또는 [alpha,l1 비율].",
    "section": "models",
    "group": "Model validation",
    "groupKo": "모형 검증",
    "controls": [
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
    "id": "bayesmean",
    "label": "Bayesian mean",
    "ko": "베이지안 평균",
    "input": "list",
    "suffix": ",0,1,2,1,0.95,0",
    "example": "bayesmean([1,2,3,4,5],0,1,2,1,0.95,0)",
    "help": "Normal sample, unknown variance; prior mu0,kappa0,alpha0,beta0; credible level; threshold. Variance ~ InvGamma(alpha0,beta0), mean | variance ~ Normal(mu0,variance/kappa0). Defaults 0,1,2,1 are proper, scale-dependent priors. Returns Student-t mean interval and next-observation predictive interval.",
    "helpKo": "분산 미지의 정규 표본; 사전 mu0,kappa0,alpha0,beta0, 구간 수준, 기준값. 분산 ~ InvGamma(alpha0,beta0), 평균|분산 ~ Normal(mu0,분산/kappa0). 기본 0,1,2,1은 자료 척도에 맞춰 조절할 적정 사전분포. 평균의 t 구간과 다음 관측 예측구간.",
    "section": "advanced",
    "group": "Bayesian inference",
    "groupKo": "베이지안 추론",
    "controls": [
      {
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "mu",
        "label": "Prior mean μ0",
        "ko": "사전 평균 μ0",
        "type": "number",
        "default": "0"
      },
      {
        "key": "kappa",
        "label": "Prior strength κ0",
        "ko": "사전 강도 κ0",
        "type": "number",
        "default": "1"
      },
      {
        "key": "alpha",
        "label": "Variance prior α0",
        "ko": "분산 사전 α0",
        "type": "number",
        "default": "2"
      },
      {
        "key": "beta",
        "label": "Variance prior β0",
        "ko": "분산 사전 β0",
        "type": "number",
        "default": "1"
      },
      {
        "key": "level",
        "label": "Credible level",
        "ko": "베이지안 구간 수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "threshold",
        "label": "Threshold mean",
        "ko": "기준 평균",
        "type": "number",
        "default": "0"
      }
    ],
    "formHelp": "Normal data, unknown variance. Adjust the normal-inverse-gamma prior to your data scale; mean interval and next-observation prediction.",
    "formHelpKo": "분산 미지의 정규 자료. 자료 척도에 맞춰 정규-역감마 사전을 조절하세요. 평균 구간·다음 관측 예측.",
    "exampleRows": [
      [
        "1"
      ],
      [
        "2"
      ],
      [
        "3"
      ],
      [
        "4"
      ],
      [
        "5"
      ]
    ]
  },
  {
    "id": "bayescompare",
    "label": "Bayesian Two-Sample Comparison",
    "ko": "베이지안 두 표본 비교",
    "input": "groups",
    "suffix": ",equal,0,0.01,2,1,0.95,20000,0",
    "example": "bayescompare([10,11,9,10,12],[13,14,12,15,13],equal,0,0.01,2,1,0.95,20000,0)",
    "help": "Two independent normal samples (at least 2 each); variance equal / unequal; mu0,kappa0,alpha0,beta0; credible level; IID posterior draws (2000-100000), seed. H1: independent Normal(mu0,variance/kappa0) means with shared (equal) or independent (unequal) InvGamma(alpha0,beta0) variances. H0: B-A=0 with nuisance prior conditioned from H1. BF10/BF01 use Savage-Dickey, not a JZS/Cauchy prior. Reports B-A mean, equal-tailed credible interval, P(muB>muA), and posterior effect (B-A)/sqrt((varianceA+varianceB)/2). Equal-mode difference summaries and BF are analytic; unequal BF uses numerical t convolution, unequal intervals/probability and effect intervals use simulation. MCSE, draws and seed are reported. Defaults are proper but unit-dependent; choose priors before inspecting outcomes.",
    "helpKo": "독립 정규 표본 두 개(각 2개 이상); 분산 equal·unequal; mu0,kappa0,alpha0,beta0; 구간 수준; IID 사후 추출 수(2000~100000), 시드. H1: 평균|분산은 독립 Normal(mu0,분산/kappa0), 분산은 공통(등분산) 또는 독립(이분산) InvGamma(alpha0,beta0). H0: B-A=0이며 H1을 이 조건으로 제한한 방해모수 사전분포를 사용합니다. BF10/BF01은 Savage-Dickey 방식이며 JZS/Cauchy 검정이 아닙니다. B-A 평균·등꼬리 신용구간·P(muB>muA)·사후 효과크기 (B-A)/sqrt((분산A+분산B)/2)를 출력합니다. 등분산 차이 요약·BF는 해석적, 이분산 BF는 t 합성곱 수치 적분, 이분산 구간·확률 및 효과크기 구간은 시뮬레이션입니다. MCSE·추출 수·시드 포함. 기본 사전분포는 적정하지만 단위에 의존하므로 결과를 보기 전에 척도에 맞게 지정하세요.",
    "section": "advanced",
    "group": "Bayesian inference",
    "groupKo": "베이지안 추론",
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
        "key": "first",
        "label": "Group A column",
        "ko": "A 그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "grouping": [
            "columns"
          ]
        }
      },
      {
        "key": "second",
        "label": "Group B column",
        "ko": "B 그룹 열",
        "type": "column",
        "default": 1,
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
      },
      {
        "key": "firstGroup",
        "label": "Group A value",
        "ko": "A 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "secondGroup",
        "label": "Group B value",
        "ko": "B 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "grouping": [
            "groups"
          ]
        }
      },
      {
        "key": "variance",
        "label": "Variance model",
        "ko": "분산 모형",
        "type": "choice",
        "default": "equal",
        "choices": [
          {
            "id": "equal",
            "label": "Equal variance",
            "ko": "등분산"
          },
          {
            "id": "unequal",
            "label": "Unequal variance",
            "ko": "이분산"
          }
        ]
      },
      {
        "key": "mu",
        "label": "Prior mean μ0 (both groups)",
        "ko": "사전 평균 μ0 (두 집단)",
        "type": "number",
        "default": "0"
      },
      {
        "key": "kappa",
        "label": "Prior strength κ0",
        "ko": "사전 강도 κ0",
        "type": "number",
        "default": "0.01"
      },
      {
        "key": "alpha",
        "label": "Variance prior α0",
        "ko": "분산 사전 α0",
        "type": "number",
        "default": "2"
      },
      {
        "key": "beta",
        "label": "Variance prior β0",
        "ko": "분산 사전 β0",
        "type": "number",
        "default": "1"
      },
      {
        "key": "level",
        "label": "Credible level",
        "ko": "베이지안 구간 수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "samples",
        "label": "Posterior draws",
        "ko": "사후 추출 수",
        "type": "number",
        "default": "20000"
      },
      {
        "key": "seed",
        "label": "Simulation seed",
        "ko": "시뮬레이션 시드",
        "type": "number",
        "default": "0"
      }
    ],
    "formHelp": "Independent groups; B - A. Blank cells are omitted separately in each selected column, so sample sizes may differ. Choose proper NIG priors in your measurement units. BF uses the H1-conditioned point null, not the default Cauchy t-test. Simulation intervals and MCSE are labeled.",
    "formHelpKo": "독립 두 집단; 차이는 B - A. 선택한 각 열의 빈 셀은 독립적으로 제외하므로 표본수가 달라도 됩니다. 측정 단위에 맞게 NIG 사전분포를 지정하세요. BF는 H1을 조건부 제한한 점귀무 모형 기준이며 기본 Cauchy t 검정과 다릅니다. 시뮬레이션 구간·MCSE를 표시합니다.",
    "exampleRows": [
      [
        "10",
        "13"
      ],
      [
        "11",
        "14"
      ],
      [
        "9",
        "12"
      ],
      [
        "10",
        "15"
      ],
      [
        "12",
        "13"
      ]
    ]
  },
  {
    "id": "bayesproportion",
    "label": "Bayesian proportion",
    "ko": "베이지안 비율",
    "input": "list",
    "suffix": ",1,1,0.95,0.5",
    "example": "bayesproportion([1,1,0,1,0,1,1,1,0,1],1,1,0.95,0.5)",
    "help": "Binary 0/1 list or [[successes,trials],...]; Beta prior alpha, beta (default 1,1); credible level; threshold p0 in (0,1). Returns equal-tailed interval, P(p>p0), next-success probability and BF10 (Beta alternative / point null p=p0).",
    "helpKo": "0/1 목록 또는 [[성공 수,시행 수],...]; Beta 사전 alpha,beta(기본 1,1), 구간 수준, 기준 p0(0~1 사이). 등꼬리 구간·P(p>p0)·다음 성공 확률·BF10(Beta 대립 / p=p0 점귀무).",
    "section": "advanced",
    "group": "Bayesian inference",
    "groupKo": "베이지안 추론",
    "controls": [
      {
        "key": "layout",
        "label": "Data",
        "ko": "자료 형태",
        "type": "choice",
        "default": "binary",
        "choices": [
          {
            "id": "binary",
            "label": "Binary observations (0/1)",
            "ko": "0/1 관측값"
          },
          {
            "id": "counts",
            "label": "Successes / trials",
            "ko": "성공 수·시행 수"
          }
        ]
      },
      {
        "key": "column",
        "label": "Observation column",
        "ko": "관측값 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "binary"
          ]
        }
      },
      {
        "key": "successes",
        "label": "Successes",
        "ko": "성공 수 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "counts"
          ]
        }
      },
      {
        "key": "trials",
        "label": "Trials",
        "ko": "시행 수 열",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "counts"
          ]
        }
      },
      {
        "key": "alpha",
        "label": "Prior α",
        "ko": "사전 α",
        "type": "number",
        "default": "1"
      },
      {
        "key": "beta",
        "label": "Prior β",
        "ko": "사전 β",
        "type": "number",
        "default": "1"
      },
      {
        "key": "level",
        "label": "Credible level",
        "ko": "베이지안 구간 수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "threshold",
        "label": "Threshold p0",
        "ko": "기준 비율 p0",
        "type": "number",
        "default": "0.5"
      }
    ],
    "formHelp": "Beta prior → posterior proportion · credible interval · P(p > p0). BF10: Beta alternative / point null p=p0.",
    "formHelpKo": "Beta 사전 → 사후 비율 · 베이지안 구간 · P(p > p0). BF10: Beta 대립 / p=p0 점귀무.",
    "exampleRows": [
      [
        "1"
      ],
      [
        "1"
      ],
      [
        "0"
      ],
      [
        "1"
      ],
      [
        "0"
      ],
      [
        "1"
      ],
      [
        "1"
      ],
      [
        "1"
      ],
      [
        "0"
      ],
      [
        "1"
      ]
    ]
  },
  {
    "id": "bayesrate",
    "label": "Bayesian Poisson rate",
    "ko": "베이지안 발생률",
    "input": "list",
    "suffix": ",1,1,0.95,1",
    "example": "bayesrate([0,2,1,3,2],1,1,0.95,1)",
    "help": "Count list (one exposure unit each) or [[count,exposure],...]; Gamma prior shape, rate (inverse scale, default 1,1); credible level; nonnegative threshold. Equal-tailed rate interval and predictive count mean/SD for one exposure unit.",
    "helpKo": "횟수 목록(관측당 노출 1) 또는 [[횟수,노출량],...]; Gamma 사전 shape,rate(척도의 역수, 기본 1,1), 구간 수준, 0 이상 기준값. 발생률 등꼬리 구간과 노출 1단위의 예측 횟수 평균·SD.",
    "section": "advanced",
    "group": "Bayesian inference",
    "groupKo": "베이지안 추론",
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
            "label": "Counts (exposure = 1)",
            "ko": "횟수 (노출량 = 1)"
          },
          {
            "id": "exposure",
            "label": "Counts / exposure",
            "ko": "횟수·노출량"
          }
        ]
      },
      {
        "key": "column",
        "label": "Count column",
        "ko": "횟수 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "exposure",
        "label": "Exposure",
        "ko": "노출량 열",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "exposure"
          ]
        }
      },
      {
        "key": "alpha",
        "label": "Prior shape α",
        "ko": "사전 shape α",
        "type": "number",
        "default": "1"
      },
      {
        "key": "beta",
        "label": "Prior rate β",
        "ko": "사전 rate β",
        "type": "number",
        "default": "1"
      },
      {
        "key": "level",
        "label": "Credible level",
        "ko": "베이지안 구간 수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "threshold",
        "label": "Threshold rate",
        "ko": "기준 발생률",
        "type": "number",
        "default": "1"
      }
    ],
    "formHelp": "Gamma prior → Poisson rate · credible interval · P(rate > threshold). β is rate, not scale.",
    "formHelpKo": "Gamma 사전 → 포아송 발생률 · 베이지안 구간 · 기준 초과 확률. β는 rate(척도의 역수)입니다.",
    "exampleRows": [
      [
        "0"
      ],
      [
        "2"
      ],
      [
        "1"
      ],
      [
        "3"
      ],
      [
        "2"
      ]
    ]
  },
  {
    "id": "bootstrapci",
    "label": "Bootstrap confidence interval",
    "ko": "부트스트랩 신뢰구간",
    "input": "list",
    "suffix": ",mean,0.95,2000,0",
    "example": "bootstrapci([1,2,3,4,5,8],mean,0.95,2000,0)",
    "help": "Statistic mean / median / stdev, confidence level, resamples, seed. Percentile IID bootstrap.",
    "helpKo": "통계량 mean / median / stdev, 신뢰수준, 재추출 수, 시드. IID 백분위 방식.",
    "section": "advanced",
    "group": "Resampling",
    "groupKo": "재표집",
    "controls": [
      {
        "key": "column",
        "label": "Data column",
        "ko": "자료 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "statistic",
        "label": "Statistic",
        "ko": "통계량",
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
            "id": "stdev",
            "label": "Standard deviation",
            "ko": "표준편차"
          }
        ]
      },
      {
        "key": "level",
        "label": "Confidence level",
        "ko": "신뢰수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "samples",
        "label": "Resamples",
        "ko": "재표집 수",
        "type": "number",
        "default": "2000"
      },
      {
        "key": "seed",
        "label": "Seed",
        "ko": "시드",
        "type": "number",
        "default": "0"
      }
    ],
    "formHelp": "Choose a column and statistic, confidence level, resamples and seed. IID percentile bootstrap; not BCa.",
    "formHelpKo": "자료 열·통계량·신뢰수준·재표집 수·시드를 선택합니다. IID 백분위 부트스트랩이며 BCa는 아닙니다.",
    "exampleRows": [
      [
        "1"
      ],
      [
        "2"
      ],
      [
        "3"
      ],
      [
        "4"
      ],
      [
        "5"
      ],
      [
        "8"
      ]
    ]
  },
  {
    "id": "bayesbootstrap",
    "label": "Bayesian Bootstrap",
    "ko": "베이지안 부트스트랩",
    "input": "list",
    "suffix": ",mean,0.95,10000,0",
    "example": "bayesbootstrap([1,2,3,4,5,8],mean,0.95,10000,0)",
    "help": "Dirichlet(1,…,1) weights on IID observed values; mean / median / variance / stdev, credible level, draws, seed. Median estimate uses the ordinary sample median (average the two middle values for even n); posterior draws use the Lower weighted quantile (smallest value with weighted CDF >= 0.5); variance/SD use population weights. Equal-tailed simulated posterior interval and histogram. Two samples: bayesbootstrap(A,B,mean,0.95,10000,0,independent); paired uses shared row weights. Comparison is statistic(B) - statistic(A).",
    "helpKo": "독립 관측값의 Dirichlet(1,…,1) 가중치; mean / median / variance / stdev, 베이지안 구간 수준·추출 수·시드. 중앙값 estimate는 일반 표본 중앙값(짝수 표본은 가운데 두 값의 평균)이고 사후추출은 Lower weighted quantile(가중 누적확률이 0.5 이상인 최소값), 분산·SD는 모집단 가중치 기준. 등꼬리 사후 구간·히스토그램. 두 표본: bayesbootstrap(A,B,mean,0.95,10000,0,independent); paired는 같은 행의 가중치를 공유합니다. 차이는 통계량(B) - 통계량(A)입니다.",
    "section": "advanced",
    "group": "Resampling",
    "groupKo": "재표집",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 구성",
        "type": "choice",
        "default": "single",
        "choices": [
          {
            "id": "single",
            "label": "Single sample",
            "ko": "단일 표본"
          },
          {
            "id": "columns",
            "label": "Two columns",
            "ko": "두 컬럼"
          },
          {
            "id": "groups",
            "label": "Group / value columns",
            "ko": "그룹·값 컬럼"
          }
        ]
      },
      {
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "single"
          ]
        }
      },
      {
        "key": "first",
        "label": "Group A column",
        "ko": "A 그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "columns"
          ]
        }
      },
      {
        "key": "second",
        "label": "Group B column",
        "ko": "B 그룹 열",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "columns"
          ]
        }
      },
      {
        "key": "comparison",
        "label": "Comparison",
        "ko": "비교 방식",
        "type": "choice",
        "default": "independent",
        "choices": [
          {
            "id": "independent",
            "label": "Independent samples",
            "ko": "독립 표본"
          },
          {
            "id": "paired",
            "label": "Paired rows",
            "ko": "대응 행"
          }
        ],
        "when": {
          "layout": [
            "columns",
            "groups"
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
          "layout": [
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
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "order",
        "label": "Group A",
        "ko": "A 그룹",
        "type": "choice",
        "default": "first",
        "choices": [
          {
            "id": "first",
            "label": "First observed group",
            "ko": "먼저 나온 그룹"
          },
          {
            "id": "reverse",
            "label": "Second observed group",
            "ko": "둘째로 나온 그룹"
          }
        ],
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "statistic",
        "label": "Statistic",
        "ko": "통계량",
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
            "label": "Median (sample estimate / weighted posterior)",
            "ko": "중앙값 (표본 추정치 / 가중 사후추출)"
          },
          {
            "id": "variance",
            "label": "Population variance",
            "ko": "모분산"
          },
          {
            "id": "stdev",
            "label": "Population SD",
            "ko": "모표준편차"
          }
        ]
      },
      {
        "key": "level",
        "label": "Credible level",
        "ko": "베이지안 구간 수준",
        "type": "number",
        "default": "0.95"
      },
      {
        "key": "samples",
        "label": "Posterior draws",
        "ko": "사후 추출 수",
        "type": "number",
        "default": "10000"
      },
      {
        "key": "seed",
        "label": "Seed",
        "ko": "시드",
        "type": "number",
        "default": "0"
      },
      {
        "key": "firstGroup",
        "label": "Group A value",
        "ko": "A 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "secondGroup",
        "label": "Group B value",
        "ko": "B 그룹 값",
        "type": "group",
        "default": "",
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "matching",
        "label": "Pair matching",
        "ko": "대응 연결",
        "type": "choice",
        "default": "order",
        "choices": [
          {
            "id": "order",
            "label": "Within-group row order",
            "ko": "그룹 안의 행 순서"
          },
          {
            "id": "subject",
            "label": "Subject ID",
            "ko": "대상 ID"
          }
        ],
        "when": {
          "layout": [
            "groups"
          ]
        }
      },
      {
        "key": "subject",
        "label": "Subject ID",
        "ko": "대상 ID 열",
        "type": "column",
        "default": 0,
        "when": {
          "matching": [
            "subject"
          ],
          "layout": [
            "groups"
          ]
        }
      }
    ],
    "formHelp": "Single sample or statistic(B) − statistic(A). Independent columns omit blanks separately; paired columns require complete matching rows and share Dirichlet weights (difference of marginal statistics, not the statistic of row differences). Group/value columns require exactly two labels and complete selected rows; choose which observed group is A. Dirichlet(1,…,1) weights; equal-tailed posterior credible interval, P(difference > 0), P(difference < 0), P(difference = 0) and histogram. Median estimate is the ordinary sample median (average the two middle values for even n); posterior draws use the Lower weighted quantile (smallest value with weighted CDF >= 0.5); variance/SD use population weights. Seed makes draws reproducible.",
    "formHelpKo": "단일 표본 또는 통계량(B) − 통계량(A)을 분석합니다. 독립 컬럼은 빈 셀을 각각 제외하며, 대응 컬럼은 완전한 같은 행에 공통 Dirichlet 가중치를 적용합니다(각 컬럼 통계량의 차이이며, 행별 차이의 통계량과 다릅니다). 그룹·값 컬럼은 두 그룹과 완전한 선택 행이 필요하며 먼저·둘째로 나온 그룹 중 A를 선택합니다. Dirichlet(1,…,1) 가중치·등꼬리 사후 구간·P(차이 > 0)·P(차이 < 0)·P(차이 = 0)·히스토그램. 중앙값 estimate는 일반 표본 중앙값(짝수 표본은 가운데 두 값의 평균)이고 사후추출은 Lower weighted quantile(가중 누적확률이 0.5 이상인 최소값), 분산·SD는 모집단 가중치 기준이며 시드로 재현합니다.",
    "exampleRows": [
      [
        "1"
      ],
      [
        "2"
      ],
      [
        "3"
      ],
      [
        "4"
      ],
      [
        "5"
      ],
      [
        "8"
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
    "helpKo": "행=관측, 열=변수; 주성분 수, 표준화 1/0.",
    "section": "advanced",
    "group": "Multivariate analysis",
    "groupKo": "다변량 분석",
    "controls": [
      {
        "key": "columns",
        "label": "Feature columns",
        "ko": "변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "components",
        "label": "Components",
        "ko": "주성분 수",
        "type": "number",
        "default": "2"
      },
      {
        "key": "standardize",
        "label": "Scaling",
        "ko": "척도",
        "type": "choice",
        "default": "1",
        "choices": [
          {
            "id": "1",
            "label": "Standardize (sample SD)",
            "ko": "표준화 (표본 표준편차)"
          },
          {
            "id": "0",
            "label": "Center only",
            "ko": "중심화만"
          }
        ]
      }
    ],
    "formHelp": "Choose numeric feature columns, components and sample-SD standardization or centering only. Selected rows must be complete. Scree plot includes all components; score and loading plots use the retained components. Loading arrows show eigenvector coefficients, on separate axes from scores.",
    "formHelpKo": "숫자 변수 열·주성분 수·표본 표준편차 표준화 또는 중심화를 선택합니다. 선택한 열의 모든 행이 완전해야 합니다. 설명분산 그래프는 모든 주성분을, 점수·로딩 그래프는 유지한 주성분을 표시합니다. 로딩 화살표는 고유벡터 계수이며 점수와 별도 좌표를 사용합니다.",
    "exampleRows": [
      [
        "1",
        "2"
      ],
      [
        "2",
        "1"
      ],
      [
        "3",
        "4"
      ],
      [
        "4",
        "3"
      ],
      [
        "5",
        "7"
      ]
    ]
  },
  {
    "id": "kmeans",
    "label": "K-means clustering",
    "ko": "K-means 군집",
    "input": "table",
    "suffix": ",2,0",
    "example": "kmeans([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)",
    "help": "Numeric feature rows; k, seed. Euclidean distance, 10 restarts, raw feature scale.",
    "helpKo": "숫자 변수 행; k, 시드. 유클리드 거리, 10회 초기화, 원래 변수 척도.",
    "section": "advanced",
    "group": "Multivariate analysis",
    "groupKo": "다변량 분석",
    "controls": [
      {
        "key": "columns",
        "label": "Feature columns",
        "ko": "변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "clusters",
        "label": "Clusters",
        "ko": "군집 수",
        "type": "number",
        "default": "2"
      },
      {
        "key": "seed",
        "label": "Seed",
        "ko": "시드",
        "type": "number",
        "default": "0"
      }
    ],
    "formHelp": "Select numeric feature columns, cluster count and seed. Uses raw feature scales, Euclidean distance and ten restarts; scale features appropriately. Plot axes show selected original features.",
    "formHelpKo": "숫자 변수 열·군집 수·시드를 선택합니다. 원래 척도의 유클리드 거리와 10회 초기화를 사용하므로 변수 척도를 확인하세요. 그래프 축은 선택한 원래 변수입니다.",
    "exampleRows": [
      [
        "1",
        "1"
      ],
      [
        "1",
        "2"
      ],
      [
        "2",
        "1"
      ],
      [
        "8",
        "8"
      ],
      [
        "8",
        "9"
      ],
      [
        "9",
        "8"
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
    "section": "advanced",
    "group": "Survival analysis",
    "groupKo": "생존분석",
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
    "id": "kaplanmeier",
    "label": "Kaplan–Meier",
    "ko": "Kaplan–Meier",
    "input": "table",
    "suffix": ",0.95",
    "example": "kaplanmeier([[1,1],[2,0],[3,1],[4,1],[5,0],[6,1]],0.95)",
    "help": "Rows: time, event (1=event, 0=censored); confidence level.",
    "helpKo": "열: 시간, 사건(1=발생, 0=중도절단); 신뢰수준.",
    "section": "advanced",
    "group": "Survival analysis",
    "groupKo": "생존분석",
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
    "section": "advanced",
    "group": "Survival analysis",
    "groupKo": "생존분석",
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
    "id": "cox",
    "label": "Cox regression",
    "ko": "Cox 회귀",
    "input": "table",
    "suffix": ",efron,-1,1",
    "example": "cox([[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]],efron,-1,1)",
    "help": "Rows: time, event 0/1, predictors. Ties efron (default) or breslow; entry column for left truncation (-1 none); PH check 0/1.",
    "helpKo": "열: 시간, 사건 0/1, 설명변수. 동률 efron(기본)/breslow, 좌측 절단 진입시간 열(-1 없음), PH 검정 0/1.",
    "section": "advanced",
    "group": "Survival analysis",
    "groupKo": "생존분석",
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
    "id": "testpower",
    "label": "Power",
    "ko": "검정력",
    "input": "none",
    "suffix": "",
    "example": "testpower(0.5,64,0.05,independent)",
    "help": "Cohen d, n per group/pairs, alpha, independent / paired / onesample, alternative two (default) / greater / less. Exact noncentral-t power.",
    "helpKo": "Cohen d, 그룹별 n/쌍 수, 유의수준, independent / paired / onesample, 대립가설 two(기본) / greater / less. 정확 noncentral-t 검정력.",
    "section": "advanced",
    "group": "Power & sample size",
    "groupKo": "검정력·표본수",
    "controls": [
      {
        "key": "design",
        "label": "Study design",
        "ko": "연구 설계",
        "type": "choice",
        "default": "independent",
        "choices": [
          {
            "id": "independent",
            "label": "Independent groups",
            "ko": "독립 두 그룹"
          },
          {
            "id": "paired",
            "label": "Paired observations",
            "ko": "대응 표본"
          },
          {
            "id": "onesample",
            "label": "One sample",
            "ko": "단일 표본"
          }
        ]
      },
      {
        "key": "effect",
        "label": "Cohen’s d",
        "ko": "Cohen의 d",
        "type": "number",
        "default": "0.5"
      },
      {
        "key": "n",
        "label": "Sample size per group / pairs",
        "ko": "그룹별 표본수·쌍 수",
        "type": "number",
        "default": "64"
      },
      {
        "key": "alpha",
        "label": "Significance level α",
        "ko": "유의수준 α",
        "type": "number",
        "default": "0.05"
      },
      {
        "key": "tail",
        "label": "Alternative hypothesis",
        "ko": "대립가설",
        "type": "choice",
        "default": "two",
        "choices": [
          {
            "id": "two",
            "label": "Two-sided",
            "ko": "양측"
          },
          {
            "id": "greater",
            "label": "Greater",
            "ko": "우측"
          },
          {
            "id": "less",
            "label": "Less",
            "ko": "좌측"
          }
        ]
      }
    ],
    "formHelp": "Set effect size, sample size, design, significance level and alternative. n is per group for independent samples, number of pairs for paired data.",
    "formHelpKo": "효과크기·표본수·연구 설계·유의수준·대립가설을 설정합니다. n은 독립 표본의 그룹별 수 또는 대응 표본의 쌍 수입니다.",
    "exampleRows": []
  },
  {
    "id": "samplesize",
    "label": "Sample size",
    "ko": "표본수",
    "input": "none",
    "suffix": "",
    "example": "samplesize(0.5,0.8,0.05,independent)",
    "help": "Cohen d, target power, alpha, design, alternative. Exact noncentral-t power.",
    "helpKo": "Cohen d, 목표 검정력, 유의수준, 설계, 대립가설. 정확 noncentral-t 검정력.",
    "section": "advanced",
    "group": "Power & sample size",
    "groupKo": "검정력·표본수",
    "controls": [
      {
        "key": "design",
        "label": "Study design",
        "ko": "연구 설계",
        "type": "choice",
        "default": "independent",
        "choices": [
          {
            "id": "independent",
            "label": "Independent groups",
            "ko": "독립 두 그룹"
          },
          {
            "id": "paired",
            "label": "Paired observations",
            "ko": "대응 표본"
          },
          {
            "id": "onesample",
            "label": "One sample",
            "ko": "단일 표본"
          }
        ]
      },
      {
        "key": "effect",
        "label": "Cohen’s d",
        "ko": "Cohen의 d",
        "type": "number",
        "default": "0.5"
      },
      {
        "key": "power",
        "label": "Target power",
        "ko": "목표 검정력",
        "type": "number",
        "default": "0.8"
      },
      {
        "key": "alpha",
        "label": "Significance level α",
        "ko": "유의수준 α",
        "type": "number",
        "default": "0.05"
      },
      {
        "key": "tail",
        "label": "Alternative hypothesis",
        "ko": "대립가설",
        "type": "choice",
        "default": "two",
        "choices": [
          {
            "id": "two",
            "label": "Two-sided",
            "ko": "양측"
          },
          {
            "id": "greater",
            "label": "Greater",
            "ko": "우측"
          },
          {
            "id": "less",
            "label": "Less",
            "ko": "좌측"
          }
        ]
      }
    ],
    "formHelp": "Set effect size and target power before collecting data. Returned n is per group or the number of pairs, according to the selected design.",
    "formHelpKo": "자료 수집 전에 효과크기·목표 검정력을 설정합니다. 산출된 n은 설계에 따라 그룹별 수 또는 대응 쌍 수입니다.",
    "exampleRows": []
  }
];
