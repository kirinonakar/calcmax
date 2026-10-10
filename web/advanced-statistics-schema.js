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
    "id": "propztest",
    "label": "One-sample proportion z test",
    "ko": "단일 표본 비율 z 검정",
    "input": "table",
    "suffix": "",
    "example": "propztest(0.5,[[60,100]])",
    "help": "propztest(p0,data) or propztest(p0,successes,trials); binary 0/1 data or [[successes,trials],...]. Optional both / left / right. Null-based standard error, no continuity correction; independent observations and adequate expected counts are required.",
    "helpKo": "propztest(p0,자료) 또는 propztest(p0,성공수,시행수); 0/1 자료 또는 [[성공수,시행수],...]. 선택적 both·left·right. 귀무가설의 표준오차, 연속성 보정 없음. 독립 관측·충분한 기대빈도가 필요합니다.",
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 구성",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "Successes / trials",
            "ko": "성공 수·시행 수"
          },
          {
            "id": "binary",
            "label": "Binary observations",
            "ko": "이항 관측값"
          }
        ]
      },
      {
        "key": "successes",
        "label": "Successes column",
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
        "label": "Trials column",
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
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "binary"
          ]
        }
      },
      {
        "key": "successValue",
        "label": "Success value",
        "ko": "성공 값",
        "type": "text",
        "default": "1",
        "when": {
          "layout": [
            "binary"
          ]
        }
      },
      {
        "key": "p0",
        "label": "Null proportion p0",
        "ko": "귀무가설 비율 p0",
        "type": "number",
        "default": "0.5"
      },
      {
        "key": "tail",
        "label": "Alternative",
        "ko": "대립가설",
        "type": "choice",
        "default": "both",
        "choices": [
          {
            "id": "both",
            "label": "Two-sided",
            "ko": "양측"
          },
          {
            "id": "left",
            "label": "Less than",
            "ko": "작다 (좌측)"
          },
          {
            "id": "right",
            "label": "Greater than",
            "ko": "크다 (우측)"
          }
        ]
      }
    ],
    "formHelp": "Counts: rows are success/trial batches for one population. Binary data: select a sample column and success value. H0: p=p0; p0 must be between 0 and 1. Expected successes and failures below 10 flag unreliable normal approximation.",
    "formHelpKo": "빈도 입력의 각 행은 같은 모집단의 성공 수·시행 수입니다. 이항 자료는 표본 열·성공 값을 선택합니다. H0: p=p0, p0는 0과 1 사이입니다. 기대 성공·실패 수가 10 미만이면 정규근사 주의를 표시합니다.",
    "exampleRows": [
      [
        "60",
        "100"
      ]
    ]
  },
  {
    "id": "propztest2",
    "label": "Two-sample proportion z test",
    "ko": "두 표본 비율 z 검정",
    "input": "table",
    "suffix": "",
    "example": "propztest2(60,100,45,100)",
    "help": "propztest2(A,B) or propztest2(successesA,trialsA,successesB,trialsB). H0: pA=pB; pooled standard error, no continuity correction. Optional both / left / right for A−B. Independent binary samples; paired outcomes require McNemar.",
    "helpKo": "propztest2(A,B) 또는 propztest2(성공수A,시행수A,성공수B,시행수B). H0: pA=pB, 합동 표준오차, 연속성 보정 없음. A−B에 대한 both·left·right 선택. 독립 이항 표본용이며 대응 결과는 McNemar를 사용합니다.",
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 구성",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "Successes / trials",
            "ko": "성공 수·시행 수"
          },
          {
            "id": "binary",
            "label": "Binary observations",
            "ko": "이항 관측값"
          }
        ]
      },
      {
        "key": "successes",
        "label": "Successes column",
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
        "label": "Trials column",
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
          "layout": [
            "binary"
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
          "grouping": [
            "columns"
          ],
          "layout": [
            "binary"
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
          ],
          "layout": [
            "binary"
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
          "layout": [
            "binary"
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
          "layout": [
            "binary"
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
          "layout": [
            "binary"
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
          "layout": [
            "binary"
          ]
        }
      },
      {
        "key": "successValue",
        "label": "Success value",
        "ko": "성공 값",
        "type": "text",
        "default": "1",
        "when": {
          "layout": [
            "binary"
          ]
        }
      },
      {
        "key": "tail",
        "label": "Alternative",
        "ko": "대립가설",
        "type": "choice",
        "default": "both",
        "choices": [
          {
            "id": "both",
            "label": "Two-sided",
            "ko": "양측"
          },
          {
            "id": "left",
            "label": "Less than",
            "ko": "작다 (좌측)"
          },
          {
            "id": "right",
            "label": "Greater than",
            "ko": "크다 (우측)"
          }
        ]
      }
    ],
    "formHelp": "Counts: exactly two rows, Group A then Group B. Binary data: independent columns or group/value columns with a common success value. H0: pA=pB; left/right alternatives refer to A−B. Uses the pooled null proportion; paired outcomes require McNemar.",
    "formHelpKo": "빈도 입력은 정확히 두 행이며 A 그룹·B 그룹 순서입니다. 이항 자료는 독립된 두 열 또는 그룹·값 열과 공통 성공 값을 선택합니다. H0: pA=pB, 좌측·우측은 A−B 기준입니다. 귀무가설의 합동 비율을 사용하며 대응 결과는 McNemar를 사용합니다.",
    "exampleRows": [
      [
        "60",
        "100"
      ],
      [
        "45",
        "100"
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
    "id": "cramerv",
    "label": "Cramér’s V",
    "ko": "Cramér의 V",
    "input": "table",
    "suffix": "",
    "example": "cramerv([[20,5],[7,18]])",
    "help": "Nonnegative integer contingency counts; uncorrected Pearson χ² and Cramér’s V. Independent observations; sparse counts can invalidate χ² p values.",
    "helpKo": "음이 아닌 정수 분할표 빈도; 보정 없는 Pearson χ²·Cramér의 V. 독립 관측; 희소 빈도에서는 χ² p값이 부정확할 수 있습니다.",
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 형태",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "Contingency counts",
            "ko": "분할표 빈도"
          },
          {
            "id": "pairs",
            "label": "Paired categories",
            "ko": "범주 쌍"
          }
        ]
      },
      {
        "key": "columns",
        "label": "Count columns",
        "ko": "빈도 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "layout": [
            "counts"
          ]
        }
      },
      {
        "key": "first",
        "label": "First variable / rater",
        "ko": "첫 변수·평가자",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      },
      {
        "key": "second",
        "label": "Second variable / rater",
        "ko": "둘째 변수·평가자",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      }
    ],
    "formHelp": "Nonnegative integer contingency counts; uncorrected Pearson χ² and Cramér’s V. Independent observations; sparse counts can invalidate χ² p values.",
    "formHelpKo": "음이 아닌 정수 분할표 빈도; 보정 없는 Pearson χ²·Cramér의 V. 독립 관측; 희소 빈도에서는 χ² p값이 부정확할 수 있습니다.",
    "exampleRows": [
      [
        "20",
        "5"
      ],
      [
        "7",
        "18"
      ]
    ]
  },
  {
    "id": "phi",
    "label": "Phi coefficient",
    "ko": "phi 계수",
    "input": "table",
    "suffix": "",
    "example": "phi([[20,5],[7,18]])",
    "help": "Signed phi for a 2×2 integer count table; swapping one category order reverses its sign. No Yates correction.",
    "helpKo": "2×2 정수 빈도표의 부호 있는 phi; 한 변수의 범주 순서를 바꾸면 부호가 반전됩니다. Yates 보정 없음.",
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 형태",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "Contingency counts",
            "ko": "분할표 빈도"
          },
          {
            "id": "pairs",
            "label": "Paired categories",
            "ko": "범주 쌍"
          }
        ]
      },
      {
        "key": "columns",
        "label": "Count columns",
        "ko": "빈도 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "layout": [
            "counts"
          ]
        }
      },
      {
        "key": "first",
        "label": "First variable / rater",
        "ko": "첫 변수·평가자",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      },
      {
        "key": "second",
        "label": "Second variable / rater",
        "ko": "둘째 변수·평가자",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      }
    ],
    "formHelp": "Signed phi for a 2×2 integer count table; swapping one category order reverses its sign. No Yates correction.",
    "formHelpKo": "2×2 정수 빈도표의 부호 있는 phi; 한 변수의 범주 순서를 바꾸면 부호가 반전됩니다. Yates 보정 없음.",
    "exampleRows": [
      [
        "20",
        "5"
      ],
      [
        "7",
        "18"
      ]
    ]
  },
  {
    "id": "cohenkappa",
    "label": "Cohen’s κ agreement",
    "ko": "Cohen의 κ 일치도",
    "input": "table",
    "suffix": ",unweighted",
    "example": "cohenkappa([[25,4,2],[3,20,5],[1,6,24]],unweighted)",
    "help": "Two-rater square count table with common category order. Unweighted, linear or quadratic kappa; multinomial delta-method SE and asymptotic Wald CI. Weighted categories must be ordered. Observed/expected agreement use the selected weights; exact agreement is also reported.",
    "helpKo": "공통 범주 순서의 두 평가자 정방 빈도표. 무가중·선형·제곱 가중 κ; 다항 델타법 SE·점근 Wald CI. 가중 범주는 순서형이어야 합니다. 관측·기대 일치율은 선택한 가중치를 반영하며 정확 일치율도 제공합니다.",
    "section": "general",
    "group": "Categorical data",
    "groupKo": "범주형 자료",
    "controls": [
      {
        "key": "layout",
        "label": "Data layout",
        "ko": "자료 형태",
        "type": "choice",
        "default": "counts",
        "choices": [
          {
            "id": "counts",
            "label": "Contingency counts",
            "ko": "분할표 빈도"
          },
          {
            "id": "pairs",
            "label": "Paired categories",
            "ko": "범주 쌍"
          }
        ]
      },
      {
        "key": "columns",
        "label": "Count columns",
        "ko": "빈도 열",
        "type": "columns",
        "default": "auto",
        "when": {
          "layout": [
            "counts"
          ]
        }
      },
      {
        "key": "first",
        "label": "First variable / rater",
        "ko": "첫 변수·평가자",
        "type": "column",
        "default": 0,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      },
      {
        "key": "second",
        "label": "Second variable / rater",
        "ko": "둘째 변수·평가자",
        "type": "column",
        "default": 1,
        "when": {
          "layout": [
            "pairs"
          ]
        }
      },
      {
        "key": "weights",
        "label": "Weights",
        "ko": "가중치",
        "type": "choice",
        "default": "unweighted",
        "choices": [
          {
            "id": "unweighted",
            "label": "Unweighted",
            "ko": "무가중"
          },
          {
            "id": "linear",
            "label": "Linear",
            "ko": "선형"
          },
          {
            "id": "quadratic",
            "label": "Quadratic",
            "ko": "제곱"
          }
        ]
      },
      {
        "key": "categories",
        "label": "Category order (comma separated; optional for unweighted)",
        "ko": "범주 순서 (쉼표 구분; 무가중은 선택)",
        "type": "text",
        "default": "",
        "when": {
          "layout": [
            "pairs"
          ]
        }
      }
    ],
    "formHelp": "Two-rater square count table with common category order. Unweighted, linear or quadratic kappa; multinomial delta-method SE and asymptotic Wald CI. Weighted categories must be ordered. Observed/expected agreement use the selected weights; exact agreement is also reported.",
    "formHelpKo": "공통 범주 순서의 두 평가자 정방 빈도표. 무가중·선형·제곱 가중 κ; 다항 델타법 SE·점근 Wald CI. 가중 범주는 순서형이어야 합니다. 관측·기대 일치율은 선택한 가중치를 반영하며 정확 일치율도 제공합니다.",
    "exampleRows": [
      [
        "25",
        "4",
        "2"
      ],
      [
        "3",
        "20",
        "5"
      ],
      [
        "1",
        "6",
        "24"
      ]
    ]
  },
  {
    "id": "cronbach",
    "label": "Cronbach α reliability",
    "ko": "Cronbach α 신뢰도",
    "input": "table",
    "suffix": ",raw",
    "example": "cronbach([[1.987,2.255,1.895,1.448,2.702,2.043],[3.103,3.653,2.588,3.608,3.265,3.866],[5.16,4.181,6.042,4.848,4.128,5.317],[1.995,2.767,1.417,-0.304,-1.529,-0.2],[1.802,1.7,2.56,2.591,4.452,3.681],[5.242,3.886,4.375,5.082,5.152,4.701],[3.057,2.704,2.315,2.034,0.528,0.659],[4.576,5.304,4.688,5.573,4.66,5.626],[5.08,3.257,4.251,3.415,3.076,4.508],[2.502,2.733,2.919,3.058,3.403,2.427],[4.23,3.357,4.235,4.595,5.16,5.999],[2.86,3.515,2.619,0.955,1.766,0.26],[3.16,2.893,2.37,2.341,1.676,2.201],[1.101,3.477,2.018,2.67,2.235,3.664],[3.503,4.223,4.268,2.178,3.518,1.656],[3.623,3.309,4.28,2.605,3.676,3.647],[4.285,5.139,6.064,4.069,4.097,4.629],[3.301,2.767,3.271,2.832,3.249,3.621],[2.857,2.677,2.348,3.272,2.696,3.272],[0.449,1.323,0.652,3.013,3.114,1.652],[3.447,3.14,2.42,2.311,3.202,2.063],[2.401,2.196,3.998,1.025,2.349,1.691],[3.54,5.121,3.878,2.99,2.849,3.135],[3.67,4.081,4.264,3.472,3.458,4.419]],raw)",
    "help": "Rows are subjects, columns are items; raw or standardized alpha. Reverse-code items first. Returns corrected item-total correlations and alpha if deleted. Alpha measures internal consistency.",
    "helpKo": "행은 대상, 열은 문항; raw·standardized α. 역문항은 먼저 역코딩합니다. 수정 문항-총점 상관·문항 삭제 시 α를 제공합니다. α는 내적 일관성을 나타냅니다.",
    "section": "general",
    "group": "Reliability",
    "groupKo": "신뢰도",
    "controls": [
      {
        "key": "columns",
        "label": "Feature / item columns",
        "ko": "변수·문항 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "mode",
        "label": "Alpha",
        "ko": "α",
        "type": "choice",
        "default": "raw",
        "choices": [
          {
            "id": "raw",
            "label": "Raw",
            "ko": "원척도"
          },
          {
            "id": "standardized",
            "label": "Standardized",
            "ko": "표준화"
          }
        ]
      }
    ],
    "formHelp": "Rows are subjects, columns are items; raw or standardized alpha. Reverse-code items first. Returns corrected item-total correlations and alpha if deleted. Alpha measures internal consistency.",
    "formHelpKo": "행은 대상, 열은 문항; raw·standardized α. 역문항은 먼저 역코딩합니다. 수정 문항-총점 상관·문항 삭제 시 α를 제공합니다. α는 내적 일관성을 나타냅니다.",
    "exampleRows": [
      [
        "1.987",
        "2.255",
        "1.895",
        "1.448",
        "2.702",
        "2.043"
      ],
      [
        "3.103",
        "3.653",
        "2.588",
        "3.608",
        "3.265",
        "3.866"
      ],
      [
        "5.16",
        "4.181",
        "6.042",
        "4.848",
        "4.128",
        "5.317"
      ],
      [
        "1.995",
        "2.767",
        "1.417",
        "-0.304",
        "-1.529",
        "-0.2"
      ],
      [
        "1.802",
        "1.7",
        "2.56",
        "2.591",
        "4.452",
        "3.681"
      ],
      [
        "5.242",
        "3.886",
        "4.375",
        "5.082",
        "5.152",
        "4.701"
      ],
      [
        "3.057",
        "2.704",
        "2.315",
        "2.034",
        "0.528",
        "0.659"
      ],
      [
        "4.576",
        "5.304",
        "4.688",
        "5.573",
        "4.66",
        "5.626"
      ],
      [
        "5.08",
        "3.257",
        "4.251",
        "3.415",
        "3.076",
        "4.508"
      ],
      [
        "2.502",
        "2.733",
        "2.919",
        "3.058",
        "3.403",
        "2.427"
      ],
      [
        "4.23",
        "3.357",
        "4.235",
        "4.595",
        "5.16",
        "5.999"
      ],
      [
        "2.86",
        "3.515",
        "2.619",
        "0.955",
        "1.766",
        "0.26"
      ],
      [
        "3.16",
        "2.893",
        "2.37",
        "2.341",
        "1.676",
        "2.201"
      ],
      [
        "1.101",
        "3.477",
        "2.018",
        "2.67",
        "2.235",
        "3.664"
      ],
      [
        "3.503",
        "4.223",
        "4.268",
        "2.178",
        "3.518",
        "1.656"
      ],
      [
        "3.623",
        "3.309",
        "4.28",
        "2.605",
        "3.676",
        "3.647"
      ],
      [
        "4.285",
        "5.139",
        "6.064",
        "4.069",
        "4.097",
        "4.629"
      ],
      [
        "3.301",
        "2.767",
        "3.271",
        "2.832",
        "3.249",
        "3.621"
      ],
      [
        "2.857",
        "2.677",
        "2.348",
        "3.272",
        "2.696",
        "3.272"
      ],
      [
        "0.449",
        "1.323",
        "0.652",
        "3.013",
        "3.114",
        "1.652"
      ],
      [
        "3.447",
        "3.14",
        "2.42",
        "2.311",
        "3.202",
        "2.063"
      ],
      [
        "2.401",
        "2.196",
        "3.998",
        "1.025",
        "2.349",
        "1.691"
      ],
      [
        "3.54",
        "5.121",
        "3.878",
        "2.99",
        "2.849",
        "3.135"
      ],
      [
        "3.67",
        "4.081",
        "4.264",
        "3.472",
        "3.458",
        "4.419"
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
    "formHelp": "Choose a numeric sample column to test normality. Interpret the p value with the Q–Q plot alongside the sample distribution.",
    "formHelpKo": "정규성을 검정할 숫자 표본 열을 선택합니다. p값은 Q–Q plot·표본 분포와 함께 해석합니다.",
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
    "id": "dunn",
    "label": "Dunn post-hoc test",
    "ko": "Dunn 사후검정",
    "input": "table",
    "suffix": ",holm",
    "example": "dunn([[1,2,4],[2,3,6],[3,5,8],[4,7,9]],holm)",
    "help": "List of independent sample lists, then holm (default), bonferroni, fdr or none. Pooled midranks, tie correction, two-sided normal p values. Retains the Kruskal–Wallis pooled rank scale.",
    "helpKo": "독립 표본 목록들의 목록과 holm(기본)·bonferroni·fdr·none. 전체 평균순위·동점 보정·양측 정규 p값. Kruskal–Wallis의 전체 순위 척도를 유지합니다.",
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
      },
      {
        "key": "adjustment",
        "label": "P-value adjustment",
        "ko": "p값 보정",
        "type": "choice",
        "default": "holm",
        "choices": [
          {
            "id": "holm",
            "label": "Holm",
            "ko": "Holm"
          },
          {
            "id": "bonferroni",
            "label": "Bonferroni",
            "ko": "Bonferroni"
          },
          {
            "id": "fdr",
            "label": "FDR (BH)",
            "ko": "FDR (BH)"
          },
          {
            "id": "none",
            "label": "None",
            "ko": "None"
          }
        ]
      }
    ],
    "formHelp": "List of independent sample lists, then holm (default), bonferroni, fdr or none. Pooled midranks, tie correction, two-sided normal p values. Retains the Kruskal–Wallis pooled rank scale.",
    "formHelpKo": "독립 표본 목록들의 목록과 holm(기본)·bonferroni·fdr·none. 전체 평균순위·동점 보정·양측 정규 p값. Kruskal–Wallis의 전체 순위 척도를 유지합니다.",
    "exampleRows": [
      [
        "1",
        "2",
        "3",
        "4"
      ],
      [
        "2",
        "3",
        "5",
        "7"
      ],
      [
        "4",
        "6",
        "8",
        "9"
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
    "id": "manova",
    "label": "MANOVA",
    "ko": "MANOVA (다변량 분산분석)",
    "input": "table",
    "suffix": "",
    "example": "manova([[1,1.987,1.448],[1,3.103,3.608],[1,5.16,4.848],[1,1.995,-0.304],[1,1.802,2.591],[1,5.242,5.082],[1,3.057,2.034],[1,4.576,5.573],[2,5.08,3.415],[2,2.502,3.058],[2,4.23,4.595],[2,2.86,0.955],[2,3.16,2.341],[2,1.101,2.67],[2,3.503,2.178],[2,3.623,2.605],[3,4.285,4.069],[3,3.301,2.832],[3,2.857,3.272],[3,0.449,3.013],[3,3.447,2.311],[3,2.401,1.025],[3,3.54,2.99],[3,3.67,3.472]])",
    "help": "One-way: group column followed by response columns. Factorial: manova(data,factorial,factor count,interaction order), categorical factors first; Type III tests with sum contrasts. Repeated: manova(wide data,repeated,occasions), one subject per row and equal-sized response blocks in occasion order. Tests mean equality across occasions using within-subject contrasts. Reports Pillai, Wilks Rao, Hotelling–Lawley F and Roy upper-bound F. Use complete data and nonsingular residual covariance; independent subjects and normal errors are assumed, with equal group covariance for independent-group designs.",
    "helpKo": "일요인: 그룹 열 뒤에 종속변수 열을 배치합니다. 요인설계: manova(자료,factorial,요인 수,상호작용 차수), 범주 요인 열을 먼저 배치하며 합 대비의 Type III 검정을 사용합니다. 반복측정: manova(넓은 자료,repeated,시점 수), 행당 대상 1명·시점 순서의 동일 크기 반응 블록입니다. 대상 내 대비로 시점 간 평균 동일성을 검정합니다. Pillai·Wilks Rao·Hotelling–Lawley F·Roy 상한 F를 제공합니다. 완전자료·비특이 잔차 공분산이 필요하며 독립 대상·정규 오차, 독립 그룹 설계의 등공분산을 가정합니다.",
    "section": "tests",
    "group": "Group comparisons",
    "groupKo": "그룹 비교",
    "controls": [
      {
        "key": "design",
        "label": "Design",
        "ko": "설계",
        "type": "choice",
        "default": "oneway",
        "choices": [
          {
            "id": "oneway",
            "label": "One factor",
            "ko": "일요인"
          },
          {
            "id": "factorial",
            "label": "Factorial (Type III)",
            "ko": "요인설계 (Type III)"
          },
          {
            "id": "repeated",
            "label": "Repeated (wide rows)",
            "ko": "반복측정 (대상별 넓은 행)"
          }
        ]
      },
      {
        "key": "group",
        "label": "Group",
        "ko": "그룹 열",
        "type": "column",
        "default": 0,
        "when": {
          "design": [
            "oneway"
          ]
        }
      },
      {
        "key": "factorColumns",
        "label": "Factor columns",
        "ko": "요인 열",
        "type": "columns",
        "default": "0,1",
        "when": {
          "design": [
            "factorial"
          ]
        }
      },
      {
        "key": "responses",
        "label": "Response columns (occasion blocks for repeated)",
        "ko": "종속변수 열 (반복측정은 시점별 블록 순서)",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "order",
        "label": "Interaction order",
        "ko": "상호작용 차수",
        "type": "number",
        "default": "2",
        "when": {
          "design": [
            "factorial"
          ]
        }
      },
      {
        "key": "occasions",
        "label": "Repeated occasions",
        "ko": "반복 시점 수",
        "type": "number",
        "default": "3",
        "when": {
          "design": [
            "repeated"
          ]
        }
      }
    ],
    "formHelp": "One-way: group column followed by response columns. Factorial: manova(data,factorial,factor count,interaction order), categorical factors first; Type III tests with sum contrasts. Repeated: manova(wide data,repeated,occasions), one subject per row and equal-sized response blocks in occasion order. Tests mean equality across occasions using within-subject contrasts. Reports Pillai, Wilks Rao, Hotelling–Lawley F and Roy upper-bound F. Use complete data and nonsingular residual covariance; independent subjects and normal errors are assumed, with equal group covariance for independent-group designs.",
    "formHelpKo": "일요인: 그룹 열 뒤에 종속변수 열을 배치합니다. 요인설계: manova(자료,factorial,요인 수,상호작용 차수), 범주 요인 열을 먼저 배치하며 합 대비의 Type III 검정을 사용합니다. 반복측정: manova(넓은 자료,repeated,시점 수), 행당 대상 1명·시점 순서의 동일 크기 반응 블록입니다. 대상 내 대비로 시점 간 평균 동일성을 검정합니다. Pillai·Wilks Rao·Hotelling–Lawley F·Roy 상한 F를 제공합니다. 완전자료·비특이 잔차 공분산이 필요하며 독립 대상·정규 오차, 독립 그룹 설계의 등공분산을 가정합니다.",
    "exampleRows": [
      [
        "1",
        "1.987",
        "1.448"
      ],
      [
        "1",
        "3.103",
        "3.608"
      ],
      [
        "1",
        "5.16",
        "4.848"
      ],
      [
        "1",
        "1.995",
        "-0.304"
      ],
      [
        "1",
        "1.802",
        "2.591"
      ],
      [
        "1",
        "5.242",
        "5.082"
      ],
      [
        "1",
        "3.057",
        "2.034"
      ],
      [
        "1",
        "4.576",
        "5.573"
      ],
      [
        "2",
        "5.08",
        "3.415"
      ],
      [
        "2",
        "2.502",
        "3.058"
      ],
      [
        "2",
        "4.23",
        "4.595"
      ],
      [
        "2",
        "2.86",
        "0.955"
      ],
      [
        "2",
        "3.16",
        "2.341"
      ],
      [
        "2",
        "1.101",
        "2.67"
      ],
      [
        "2",
        "3.503",
        "2.178"
      ],
      [
        "2",
        "3.623",
        "2.605"
      ],
      [
        "3",
        "4.285",
        "4.069"
      ],
      [
        "3",
        "3.301",
        "2.832"
      ],
      [
        "3",
        "2.857",
        "3.272"
      ],
      [
        "3",
        "0.449",
        "3.013"
      ],
      [
        "3",
        "3.447",
        "2.311"
      ],
      [
        "3",
        "2.401",
        "1.025"
      ],
      [
        "3",
        "3.54",
        "2.99"
      ],
      [
        "3",
        "3.67",
        "3.472"
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
    "id": "tinterval",
    "label": "t confidence interval",
    "ko": "t 신뢰구간",
    "input": "list",
    "suffix": ",95",
    "example": "tinterval(95,[1,2,3,4,5])",
    "help": "Mean confidence interval with unknown population SD: tinterval(level,data) or tinterval(level,mean,SD,n).",
    "helpKo": "모집단 표준편차를 모르는 평균의 신뢰구간: tinterval(수준,자료) 또는 tinterval(수준,평균,표준편차,n).",
    "section": "tests",
    "group": "Confidence intervals",
    "groupKo": "신뢰구간",
    "controls": [
      {
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "level",
        "label": "Confidence level (%)",
        "ko": "신뢰수준 (%)",
        "type": "number",
        "default": "95"
      }
    ],
    "formHelp": "Select a sample column and confidence level. Uses the sample SD and Student t distribution for the mean interval.",
    "formHelpKo": "표본 열과 신뢰수준을 선택합니다. 표본 표준편차와 Student t 분포로 평균 구간을 계산합니다.",
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
    "id": "zinterval",
    "label": "z confidence interval",
    "ko": "z 신뢰구간",
    "input": "list",
    "suffix": ",95,2",
    "example": "zinterval(95,2,[1,2,3,4,5])",
    "help": "Mean confidence interval with known population SD: zinterval(level,sigma,data) or zinterval(level,sigma,mean,n).",
    "helpKo": "모집단 표준편차를 아는 평균의 신뢰구간: zinterval(수준,모집단표준편차,자료) 또는 zinterval(수준,모집단표준편차,평균,n).",
    "section": "tests",
    "group": "Confidence intervals",
    "groupKo": "신뢰구간",
    "controls": [
      {
        "key": "column",
        "label": "Sample column",
        "ko": "표본 열",
        "type": "column",
        "default": 0
      },
      {
        "key": "level",
        "label": "Confidence level (%)",
        "ko": "신뢰수준 (%)",
        "type": "number",
        "default": "95"
      },
      {
        "key": "sigma",
        "label": "Known population SD σ",
        "ko": "알려진 모집단 표준편차 σ",
        "type": "number",
        "default": "2"
      }
    ],
    "formHelp": "Select a sample column and confidence level; enter the known population SD.",
    "formHelpKo": "표본 열·신뢰수준을 선택하고 알려진 모집단 표준편차를 입력합니다.",
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
    "id": "zeroinflated",
    "label": "Zero-inflated regression (ZIP/ZINB)",
    "ko": "영과잉 회귀 (ZIP/ZINB)",
    "input": "table",
    "suffix": ",poisson,intercept",
    "example": "zeroinflated([[0,0],[0,0],[0,0],[0,1],[0,2],[0,3],[1,0],[1,0],[1,1],[1,2],[1,3],[1,5],[2,0],[2,0],[2,1],[2,3],[2,5],[2,8],[3,0],[3,0],[3,2],[3,4],[3,7],[3,10]],poisson,intercept)",
    "help": "Rows: predictors, integer counts. Poisson or estimated-alpha NB2 count mixture with logit structural zeros; inflation intercept or same predictors. Joint ML Wald inference.",
    "helpKo": "열: 설명변수·정수 빈도. Poisson 또는 alpha 추정 NB2와 구조적 0의 logit 혼합; 영과잉 절편 또는 같은 설명변수. 공동 ML Wald 추론;",
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
        "key": "family",
        "label": "Count family",
        "ko": "빈도 분포",
        "type": "choice",
        "default": "poisson",
        "choices": [
          {
            "id": "poisson",
            "label": "Poisson (ZIP)",
            "ko": "Poisson (ZIP)"
          },
          {
            "id": "nbinom",
            "label": "Negative binomial (ZINB2)",
            "ko": "음이항 (ZINB2)"
          }
        ]
      },
      {
        "key": "inflation",
        "label": "Inflation predictors",
        "ko": "영과잉 설명변수",
        "type": "choice",
        "default": "intercept",
        "choices": [
          {
            "id": "intercept",
            "label": "Intercept only",
            "ko": "절편만"
          },
          {
            "id": "same",
            "label": "Same predictors",
            "ko": "같은 설명변수"
          }
        ]
      }
    ],
    "formHelp": "Rows: predictors, integer counts. Poisson or estimated-alpha NB2 count mixture with logit structural zeros; inflation intercept or same predictors. Joint ML Wald inference.",
    "formHelpKo": "열: 설명변수·정수 빈도. Poisson 또는 alpha 추정 NB2와 구조적 0의 logit 혼합; 영과잉 절편 또는 같은 설명변수. 공동 ML Wald 추론;",
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
        "0",
        "3"
      ],
      [
        "1",
        "0"
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
        "2"
      ],
      [
        "1",
        "3"
      ],
      [
        "1",
        "5"
      ],
      [
        "2",
        "0"
      ],
      [
        "2",
        "0"
      ],
      [
        "2",
        "1"
      ],
      [
        "2",
        "3"
      ],
      [
        "2",
        "5"
      ],
      [
        "2",
        "8"
      ],
      [
        "3",
        "0"
      ],
      [
        "3",
        "0"
      ],
      [
        "3",
        "2"
      ],
      [
        "3",
        "4"
      ],
      [
        "3",
        "7"
      ],
      [
        "3",
        "10"
      ]
    ]
  },
  {
    "id": "tobit",
    "label": "Tobit censored regression",
    "ko": "Tobit 검열 회귀",
    "input": "table",
    "suffix": ",0,none",
    "example": "tobit([[0,0],[1,0],[2,1],[3,3],[4,3],[5,6],[6,5],[7,8],[8,9],[9,8],[10,11],[11,12]],0,none)",
    "help": "Rows: predictors, observed response; lower bound (default 0), upper bound (default none). Type-I normal censoring, joint ML coefficient/sigma inference. Values at bounds are censored; coefficients refer to the latent response.",
    "helpKo": "열: 설명변수·관측 반응; 하한(기본 0)·상한(기본 none). Type-I 정규 검열·공동 ML 계수/sigma 추론. 경계값은 검열 관측, 계수는 잠재 반응 기준.",
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
        "key": "lower",
        "label": "Lower bound (none = absent)",
        "ko": "하한 (none = 없음)",
        "type": "text",
        "default": "0"
      },
      {
        "key": "upper",
        "label": "Upper bound (none = absent)",
        "ko": "상한 (none = 없음)",
        "type": "text",
        "default": "none"
      }
    ],
    "formHelp": "Rows: predictors, observed response; lower bound (default 0), upper bound (default none). Type-I normal censoring, joint ML coefficient/sigma inference. Values at bounds are censored; coefficients refer to the latent response.",
    "formHelpKo": "열: 설명변수·관측 반응; 하한(기본 0)·상한(기본 none). Type-I 정규 검열·공동 ML 계수/sigma 추론. 경계값은 검열 관측, 계수는 잠재 반응 기준.",
    "exampleRows": [
      [
        "0",
        "0"
      ],
      [
        "1",
        "0"
      ],
      [
        "2",
        "1"
      ],
      [
        "3",
        "3"
      ],
      [
        "4",
        "3"
      ],
      [
        "5",
        "6"
      ],
      [
        "6",
        "5"
      ],
      [
        "7",
        "8"
      ],
      [
        "8",
        "9"
      ],
      [
        "9",
        "8"
      ],
      [
        "10",
        "11"
      ],
      [
        "11",
        "12"
      ]
    ]
  },
  {
    "id": "quantreg",
    "label": "Quantile regression",
    "ko": "분위회귀",
    "input": "table",
    "suffix": ",0.5",
    "example": "quantreg([[0,2],[1,4],[2,3],[3,8],[4,7],[5,9],[6,10],[7,12],[8,11],[9,15],[10,17],[11,16]],0.5)",
    "help": "Rows: predictors, response; quantile in (0,1). IRLS with subgradient optimality check; asymptotic Gaussian-kernel sandwich / Hall–Sheather bandwidth if residual density supports inference.",
    "helpKo": "열: 설명변수·반응; (0,1)의 분위수. 부분기울기 최적성 검사 IRLS; 잔차 밀도가 추론을 허용하면 Gaussian 커널 샌드위치 / Hall–Sheather 대역폭의 점근 추론.",
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
        "key": "quantile",
        "label": "Quantile",
        "ko": "분위수",
        "type": "number",
        "default": "0.5"
      }
    ],
    "formHelp": "Rows: predictors, response; quantile in (0,1). IRLS with subgradient optimality check; asymptotic Gaussian-kernel sandwich / Hall–Sheather bandwidth if residual density supports inference.",
    "formHelpKo": "열: 설명변수·반응; (0,1)의 분위수. 부분기울기 최적성 검사 IRLS; 잔차 밀도가 추론을 허용하면 Gaussian 커널 샌드위치 / Hall–Sheather 대역폭의 점근 추론.",
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
        "3"
      ],
      [
        "3",
        "8"
      ],
      [
        "4",
        "7"
      ],
      [
        "5",
        "9"
      ],
      [
        "6",
        "10"
      ],
      [
        "7",
        "12"
      ],
      [
        "8",
        "11"
      ],
      [
        "9",
        "15"
      ],
      [
        "10",
        "17"
      ],
      [
        "11",
        "16"
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
    "id": "mediation",
    "label": "Mediation analysis",
    "ko": "매개 분석",
    "input": "table",
    "suffix": ",2000,0",
    "example": "mediation([[-2.191,-1.014,-0.857],[-0.662,0.471,0.662],[1.567,1.183,3.061],[2.013,1.591,3.248],[-1.857,-0.593,-0.9],[-0.348,1.302,0.7],[2.702,1.566,3.688],[-0.934,-0.125,-1.06],[-1.674,-0.681,-1.092],[-0.145,-0.168,1.046],[0.818,0.995,2.032],[0.809,1.054,1.548],[1.027,-0.185,-0.448],[-0.783,-0.785,0.117],[-1.612,-1.911,-1.425],[0.26,0.246,-0.084],[1.802,2.034,2.866],[-0.316,-0.156,0.409],[-0.667,-1.464,-1.077],[-0.268,-0.145,-0.343],[0.58,1.473,1.633],[2.444,1.513,2.462],[0.308,0.001,0.363],[1.209,0.904,0.926]],2000,0)",
    "help": "Rows: X, M, optional covariates, Y. Single continuous mediator; adjusted OLS direct, total and indirect a×b effects, seeded row-bootstrap percentile CI and Sobel approximation.",
    "helpKo": "열: X·M·선택적 공변량·Y. 단일 연속형 매개변수; 조정 OLS 직접·총·간접 a×b 효과, 시드 기반 행 부트스트랩 백분위 CI·Sobel 근사.",
    "section": "models",
    "group": "Mediation & moderation",
    "groupKo": "매개·조절 분석",
    "controls": [
      {
        "key": "x",
        "label": "Predictor X",
        "ko": "설명변수 X",
        "type": "column",
        "default": 0
      },
      {
        "key": "middle",
        "label": "Mediator M / moderator W",
        "ko": "매개 M·조절 W",
        "type": "column",
        "default": 1
      },
      {
        "key": "response",
        "label": "Outcome Y",
        "ko": "결과 Y",
        "type": "column",
        "default": -1
      },
      {
        "key": "covariates",
        "label": "Covariate columns (optional)",
        "ko": "공변량 열 (선택)",
        "type": "columns",
        "default": ""
      },
      {
        "key": "samples",
        "label": "Bootstrap samples",
        "ko": "부트스트랩 횟수",
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
    "formHelp": "Rows: X, M, optional covariates, Y. Single continuous mediator; adjusted OLS direct, total and indirect a×b effects, seeded row-bootstrap percentile CI and Sobel approximation.",
    "formHelpKo": "열: X·M·선택적 공변량·Y. 단일 연속형 매개변수; 조정 OLS 직접·총·간접 a×b 효과, 시드 기반 행 부트스트랩 백분위 CI·Sobel 근사.",
    "exampleRows": [
      [
        "-2.191",
        "-1.014",
        "-0.857"
      ],
      [
        "-0.662",
        "0.471",
        "0.662"
      ],
      [
        "1.567",
        "1.183",
        "3.061"
      ],
      [
        "2.013",
        "1.591",
        "3.248"
      ],
      [
        "-1.857",
        "-0.593",
        "-0.9"
      ],
      [
        "-0.348",
        "1.302",
        "0.7"
      ],
      [
        "2.702",
        "1.566",
        "3.688"
      ],
      [
        "-0.934",
        "-0.125",
        "-1.06"
      ],
      [
        "-1.674",
        "-0.681",
        "-1.092"
      ],
      [
        "-0.145",
        "-0.168",
        "1.046"
      ],
      [
        "0.818",
        "0.995",
        "2.032"
      ],
      [
        "0.809",
        "1.054",
        "1.548"
      ],
      [
        "1.027",
        "-0.185",
        "-0.448"
      ],
      [
        "-0.783",
        "-0.785",
        "0.117"
      ],
      [
        "-1.612",
        "-1.911",
        "-1.425"
      ],
      [
        "0.26",
        "0.246",
        "-0.084"
      ],
      [
        "1.802",
        "2.034",
        "2.866"
      ],
      [
        "-0.316",
        "-0.156",
        "0.409"
      ],
      [
        "-0.667",
        "-1.464",
        "-1.077"
      ],
      [
        "-0.268",
        "-0.145",
        "-0.343"
      ],
      [
        "0.58",
        "1.473",
        "1.633"
      ],
      [
        "2.444",
        "1.513",
        "2.462"
      ],
      [
        "0.308",
        "0.001",
        "0.363"
      ],
      [
        "1.209",
        "0.904",
        "0.926"
      ]
    ]
  },
  {
    "id": "moderation",
    "label": "Moderation analysis",
    "ko": "조절 분석",
    "input": "table",
    "suffix": "",
    "example": "moderation([[-2.191,-1.014,-0.857],[-0.662,0.471,0.662],[1.567,1.183,3.061],[2.013,1.591,3.248],[-1.857,-0.593,-0.9],[-0.348,1.302,0.7],[2.702,1.566,3.688],[-0.934,-0.125,-1.06],[-1.674,-0.681,-1.092],[-0.145,-0.168,1.046],[0.818,0.995,2.032],[0.809,1.054,1.548],[1.027,-0.185,-0.448],[-0.783,-0.785,0.117],[-1.612,-1.911,-1.425],[0.26,0.246,-0.084],[1.802,2.034,2.866],[-0.316,-0.156,0.409],[-0.667,-1.464,-1.077],[-0.268,-0.145,-0.343],[0.58,1.473,1.633],[2.444,1.513,2.462],[0.308,0.001,0.363],[1.209,0.904,0.926]])",
    "help": "Rows: X, W, optional covariates, Y. Centered X/W and X×W interaction; conditional slopes at W mean ± SD with full covariance t inference. Continuous moderator, independent OLS errors.",
    "helpKo": "열: X·W·선택적 공변량·Y. 중심화 X/W·X×W 상호작용; W 평균±SD의 조건부 기울기·전체 공분산 t 추론. 연속형 조절변수·독립 OLS 오차.",
    "section": "models",
    "group": "Mediation & moderation",
    "groupKo": "매개·조절 분석",
    "controls": [
      {
        "key": "x",
        "label": "Predictor X",
        "ko": "설명변수 X",
        "type": "column",
        "default": 0
      },
      {
        "key": "middle",
        "label": "Mediator M / moderator W",
        "ko": "매개 M·조절 W",
        "type": "column",
        "default": 1
      },
      {
        "key": "response",
        "label": "Outcome Y",
        "ko": "결과 Y",
        "type": "column",
        "default": -1
      },
      {
        "key": "covariates",
        "label": "Covariate columns (optional)",
        "ko": "공변량 열 (선택)",
        "type": "columns",
        "default": ""
      }
    ],
    "formHelp": "Rows: X, W, optional covariates, Y. Centered X/W and X×W interaction; conditional slopes at W mean ± SD with full covariance t inference. Continuous moderator, independent OLS errors.",
    "formHelpKo": "열: X·W·선택적 공변량·Y. 중심화 X/W·X×W 상호작용; W 평균±SD의 조건부 기울기·전체 공분산 t 추론. 연속형 조절변수·독립 OLS 오차.",
    "exampleRows": [
      [
        "-2.191",
        "-1.014",
        "-0.857"
      ],
      [
        "-0.662",
        "0.471",
        "0.662"
      ],
      [
        "1.567",
        "1.183",
        "3.061"
      ],
      [
        "2.013",
        "1.591",
        "3.248"
      ],
      [
        "-1.857",
        "-0.593",
        "-0.9"
      ],
      [
        "-0.348",
        "1.302",
        "0.7"
      ],
      [
        "2.702",
        "1.566",
        "3.688"
      ],
      [
        "-0.934",
        "-0.125",
        "-1.06"
      ],
      [
        "-1.674",
        "-0.681",
        "-1.092"
      ],
      [
        "-0.145",
        "-0.168",
        "1.046"
      ],
      [
        "0.818",
        "0.995",
        "2.032"
      ],
      [
        "0.809",
        "1.054",
        "1.548"
      ],
      [
        "1.027",
        "-0.185",
        "-0.448"
      ],
      [
        "-0.783",
        "-0.785",
        "0.117"
      ],
      [
        "-1.612",
        "-1.911",
        "-1.425"
      ],
      [
        "0.26",
        "0.246",
        "-0.084"
      ],
      [
        "1.802",
        "2.034",
        "2.866"
      ],
      [
        "-0.316",
        "-0.156",
        "0.409"
      ],
      [
        "-0.667",
        "-1.464",
        "-1.077"
      ],
      [
        "-0.268",
        "-0.145",
        "-0.343"
      ],
      [
        "0.58",
        "1.473",
        "1.633"
      ],
      [
        "2.444",
        "1.513",
        "2.462"
      ],
      [
        "0.308",
        "0.001",
        "0.363"
      ],
      [
        "1.209",
        "0.904",
        "0.926"
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
    "help": "Rows: subject ID, predictors, response. Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial (0/1, logit), poisson or nbinom (NB2, log). ML adaptive Gauss-Hermite quadrature: 15 points default, 1 = Laplace, otherwise 7-31. Optional fourth argument offset vector, fifth offset / exposure. Limit 1500 rows, 8 fixed coefficients. Subject-specific effects; joint marginal observed information by central differences; asymptotic Wald inference. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.",
    "helpKo": "열: 대상 ID, 설명변수, 반응. 랜덤 절편 + 선택적 상관 랜덤 기울기 1개(일곱째 인수: 선택한 설명변수 번호, 0 없음). 기울기는 2차원 Laplace(셋째 인수 1), 번호 앞에 [],offset,likelihood를 지정합니다. 공분산·조건부 최빈값은 원래 단위로 표시합니다. 랜덤 절편: binomial(0/1, 로짓), poisson·nbinom(NB2, 로그). ML 적응형 Gauss-Hermite 적분: 기본 15점, 1=Laplace, 그 외 7~31점. 선택적 넷째 인수 오프셋 목록, 다섯째 offset·exposure. 최대 1500행·고정계수 8개. 대상별 조건부 효과, 중앙차분 관측 정보행렬·점근 Wald 추론. 소수 대상의 Wald 추론은 부정확할 수 있습니다. Laplace·31점도 다른 적분점의 우도를 비교합니다. 선택적 여섯째 인수 refit은 재적합 계수를 비교하고 0.1 SE 초과 변동이면 CI·p값을 생략합니다. 오프셋이 없으면 refit 앞에 [],offset을 사용합니다.",
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
    "formHelp": "Random intercept, optionally one correlated random slope (seventh argument: selected predictor position, 0 = none). Slopes use two-dimensional Laplace (third argument 1), use [],offset,likelihood before the slope position. Covariance and conditional modes are reported in original units. Random intercept: binomial / Poisson / NB2. ML quadrature (15 default, 1 Laplace, 7-31); conditional effects. Count families support log offset or positive exposure. Few-subject Wald inference may be unreliable. Integration checks compare likelihood at another point count, including Laplace/31 points; optional sixth argument refit compares coefficients and suppresses CI/p if shifts exceed 0.1 SE. To omit offsets use [],offset before refit.",
    "formHelpKo": "랜덤 절편 또는 절편 + 상관 기울기 1개. 선택적 일곱째 인수: 선택한 설명변수 번호(0 없음). 기울기는 Laplace(셋째 인수 1), 3개 이상 대상 내 설명변수 변화가 필요합니다. ICC는 x=0 기준이며 singular·식별 불가 적합은 Wald 추론을 생략합니다. 이항·포아송·NB2. ML 적분(기본 15점, 1 Laplace, 7~31); 조건부 효과. 빈도 분포는 로그 오프셋·양수 노출량 지원. 소수 대상의 Wald 추론은 부정확할 수 있습니다. Laplace·31점도 다른 적분점의 우도를 비교합니다. 선택적 여섯째 인수 refit은 재적합 계수를 비교하고 0.1 SE 초과 변동이면 CI·p값을 생략합니다. 오프셋이 없으면 refit 앞에 [],offset을 사용합니다.",
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
    "help": "Rows: cluster ID, predictors, response. gaussian / binomial / poisson; working correlation independence / exchangeable / ar1; fourth argument [i,j] interaction pairs; sandwich SE. Pearson dispersion-adjusted correlation; AR(1) uses row order and equal spacing. Few-cluster Wald inference may be unreliable. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. Review the cluster count when interpreting intervals.",
    "helpKo": "열: 군집 ID, 설명변수, 반응. gaussian / binomial / poisson; 작업상관 independence / exchangeable / ar1; 넷째 인수 [i,j] 상호작용 쌍; 강건 SE. Pearson 분산 보정 상관; AR(1)은 행 순서·등간격 사용. 소수 군집의 Wald 추론은 부정확할 수 있습니다. 선택적 다섯째 인수 small은 Mancl-DeRouen 공분산·군집 수-계수 수 자유도의 t 추론을 적용합니다. 상호작용이 없으면 넷째 인수는 []입니다. 구간 해석 시 군집 수를 확인하세요.",
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
    "formHelp": "Working correlation independence / exchangeable / AR(1); dispersion-adjusted correlation and cluster-robust SE. Optional fifth argument small adds Mancl-DeRouen covariance and t inference with clusters minus coefficient count df; use [] as the fourth argument when there are no interactions. Review the cluster count when interpreting intervals. AR(1): row order, equal spacing. Interactions accept header names (age,weight), the shown column letters or labels (y,z / age (y),weight (z)), column numbers (2,3) or predictor order (p1,p2); separate pairs with ;.",
    "formHelpKo": "작업상관 independence / exchangeable / AR(1); 분산 보정 상관·군집 강건 표준오차. 선택적 다섯째 인수 small은 Mancl-DeRouen 공분산·군집 수-계수 수 자유도의 t 추론을 적용합니다. 상호작용이 없으면 넷째 인수는 []입니다. 구간 해석 시 군집 수를 확인하세요. AR(1): 행 순서·등간격. 상호작용은 열 이름(age,weight), 표시된 열 문자·라벨(y,z / age (y),weight (z)), 열 번호(2,3), 설명변수 순서(p1,p2)로 입력하고 쌍은 ;로 구분합니다.",
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
    "help": "Two independent normal samples (at least 2 each); variance equal / unequal; mu0,kappa0,alpha0,beta0; credible level; IID posterior draws (2000-100000), seed. H1: independent Normal(mu0,variance/kappa0) means with shared (equal) or independent (unequal) InvGamma(alpha0,beta0) variances. H0: B-A=0 with nuisance prior conditioned from H1. BF10/BF01 use the Savage-Dickey density ratio. Reports B-A mean, equal-tailed credible interval, P(muB>muA), and posterior effect (B-A)/sqrt((varianceA+varianceB)/2). Equal-mode difference summaries and BF are analytic; unequal BF uses numerical t convolution, unequal intervals/probability and effect intervals use simulation. MCSE, draws and seed are reported. Defaults are proper but unit-dependent; choose priors before inspecting outcomes.",
    "helpKo": "독립 정규 표본 두 개(각 2개 이상); 분산 equal·unequal; mu0,kappa0,alpha0,beta0; 구간 수준; IID 사후 추출 수(2000~100000), 시드. H1: 평균|분산은 독립 Normal(mu0,분산/kappa0), 분산은 공통(등분산) 또는 독립(이분산) InvGamma(alpha0,beta0). H0: B-A=0이며 H1을 이 조건으로 제한한 방해모수 사전분포를 사용합니다. BF10/BF01은 Savage-Dickey 밀도비를 사용합니다. B-A 평균·등꼬리 신용구간·P(muB>muA)·사후 효과크기 (B-A)/sqrt((분산A+분산B)/2)를 출력합니다. 등분산 차이 요약·BF는 해석적, 이분산 BF는 t 합성곱 수치 적분, 이분산 구간·확률 및 효과크기 구간은 시뮬레이션입니다. MCSE·추출 수·시드 포함. 기본 사전분포는 적정하지만 단위에 의존하므로 결과를 보기 전에 척도에 맞게 지정하세요.",
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
    "formHelp": "Independent groups; B - A. Blank cells are omitted separately in each selected column, so sample sizes may differ. Choose proper NIG priors in your measurement units. BF compares H1 with its conditioned point null. Simulation intervals and MCSE are labeled.",
    "formHelpKo": "독립 두 집단; 차이는 B - A. 선택한 각 열의 빈 셀은 독립적으로 제외하므로 표본수가 달라도 됩니다. 측정 단위에 맞게 NIG 사전분포를 지정하세요. BF는 H1과 이를 조건부 제한한 점귀무 모형을 비교합니다. 시뮬레이션 구간·MCSE를 표시합니다.",
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
    "formHelp": "Gamma prior → Poisson rate · credible interval · P(rate > threshold). β is the rate parameter.",
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
    "formHelp": "Choose a column and statistic, confidence level, resamples and seed. IID percentile bootstrap.",
    "formHelpKo": "자료 열·통계량·신뢰수준·재표집 수·시드를 선택합니다. IID 백분위 부트스트랩입니다.",
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
    "id": "efa",
    "label": "Exploratory factor analysis (EFA)",
    "ko": "탐색적 요인분석 (EFA)",
    "input": "table",
    "suffix": ",2,varimax",
    "example": "efa([[1.987,2.255,1.895,1.448,2.702,2.043],[3.103,3.653,2.588,3.608,3.265,3.866],[5.16,4.181,6.042,4.848,4.128,5.317],[1.995,2.767,1.417,-0.304,-1.529,-0.2],[1.802,1.7,2.56,2.591,4.452,3.681],[5.242,3.886,4.375,5.082,5.152,4.701],[3.057,2.704,2.315,2.034,0.528,0.659],[4.576,5.304,4.688,5.573,4.66,5.626],[5.08,3.257,4.251,3.415,3.076,4.508],[2.502,2.733,2.919,3.058,3.403,2.427],[4.23,3.357,4.235,4.595,5.16,5.999],[2.86,3.515,2.619,0.955,1.766,0.26],[3.16,2.893,2.37,2.341,1.676,2.201],[1.101,3.477,2.018,2.67,2.235,3.664],[3.503,4.223,4.268,2.178,3.518,1.656],[3.623,3.309,4.28,2.605,3.676,3.647],[4.285,5.139,6.064,4.069,4.097,4.629],[3.301,2.767,3.271,2.832,3.249,3.621],[2.857,2.677,2.348,3.272,2.696,3.272],[0.449,1.323,0.652,3.013,3.114,1.652],[3.447,3.14,2.42,2.311,3.202,2.063],[2.401,2.196,3.998,1.025,2.349,1.691],[3.54,5.121,3.878,2.99,2.849,3.135],[3.67,4.081,4.264,3.472,3.458,4.419],[3.341,3.341,2.722,2.088,2.846,3.371],[2.491,2.816,1.44,1.192,2.551,1.975],[3.002,2.66,3.754,3.345,5.413,3.962],[2.893,3.409,3.316,3.336,3.614,4.426],[1.843,1.939,2.024,5.012,4.963,5.395],[4.404,3.051,4.195,4.157,2.983,3.546],[3.252,2.804,3.346,2.695,4.096,3.254],[3.384,3.477,4.064,1.625,1.051,0.949],[3.04,2.869,2.962,2.556,1.997,0.53],[2.398,2.777,2.227,3.809,3.915,4.636],[3.253,3.538,4.357,3.697,3.035,3.881],[2.896,2.958,0.826,0.678,1.802,0.825],[1.423,1.154,1.884,3.063,3.992,3.001],[3.389,3.692,2.134,3.7,4.094,4.75],[2.75,3.546,3.742,3.853,3.204,5.14],[3.966,2.911,2.755,2.152,3.222,1.657],[2.267,1.875,2.087,1.343,3.21,3.064],[4.436,4.095,5.016,4.363,3.93,4.141],[-0.224,1.681,0.508,1.922,1.822,2.534],[2.844,2.896,3.813,4.436,2.472,3.68],[2.548,3.279,1.79,2.175,1.88,1.092],[2.624,2.491,2.819,3.493,3.617,2.98],[3.027,3.869,2.073,2.693,2.873,3.391],[2.524,3.183,1.987,3.333,2.888,3.225]],2,varimax)",
    "help": "Select numeric item columns. Extraction: pca (principal components, default) or pa (principal axis). Enter a factor count or parallel for automatic selection. Rotations: varimax, none, promax (power 4) or oblimin (delta 0). Arguments: data,count,rotation,extraction,parallel simulations,seed,percentile. Parallel simulations: 0 = off; automatic selection defaults to 100, percentile 0.95. Reports KMO, Bartlett, communalities, pattern and structure loadings, factor correlations and regression scores. Principal axis uses SMC-reduced eigenvalues for parallel analysis; PCA uses correlation eigenvalues. For correlated factors, interpret communalities with the factor correlations.",
    "helpKo": "숫자 문항 열을 선택합니다. 추출: 주성분(pca, 기본) 또는 주축요인법(pa). 요인 수 또는 자동 선택 parallel을 입력합니다. 회전: varimax·none·promax(지수 4)·oblimin(delta 0). 인수: 자료,요인 수,회전,추출,병렬분석 횟수,시드,백분위. 횟수 0은 끔, 자동 선택 기본값은 100회·백분위 0.95입니다. KMO·Bartlett·공통성·패턴/구조 적재량·요인 상관·회귀 점수를 제공합니다. 병렬분석은 주축요인법에서 SMC 축소 고유값, 주성분에서 상관 고유값을 사용합니다. 사각회전의 공통성은 요인 상관을 함께 반영해 해석합니다.",
    "section": "advanced",
    "group": "Measurement & structural models",
    "groupKo": "측정·구조 모형",
    "controls": [
      {
        "key": "columns",
        "label": "Feature / item columns",
        "ko": "변수·문항 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "factors",
        "label": "Factor count or parallel",
        "ko": "요인 수 또는 parallel",
        "type": "text",
        "default": "2"
      },
      {
        "key": "extraction",
        "label": "Extraction",
        "ko": "추출",
        "type": "choice",
        "default": "pca",
        "choices": [
          {
            "id": "pca",
            "label": "Principal components (PCA)",
            "ko": "주성분 (PCA)"
          },
          {
            "id": "pa",
            "label": "Principal axis (common factors)",
            "ko": "주축요인법 (공통요인)"
          }
        ]
      },
      {
        "key": "rotation",
        "label": "Rotation",
        "ko": "회전",
        "type": "choice",
        "default": "varimax",
        "choices": [
          {
            "id": "varimax",
            "label": "Varimax (orthogonal)",
            "ko": "Varimax (직교)"
          },
          {
            "id": "promax",
            "label": "Promax (oblique, power 4)",
            "ko": "Promax (사각, 지수 4)"
          },
          {
            "id": "oblimin",
            "label": "Oblimin (oblique, delta 0)",
            "ko": "Oblimin (사각, delta 0)"
          },
          {
            "id": "none",
            "label": "None",
            "ko": "없음"
          }
        ]
      },
      {
        "key": "parallelSamples",
        "label": "Parallel simulations (0 = off; parallel count uses 100)",
        "ko": "병렬분석 횟수 (0=끔; parallel 자동 선택은 100회)",
        "type": "number",
        "default": "0"
      },
      {
        "key": "seed",
        "label": "Seed",
        "ko": "시드",
        "type": "number",
        "default": "0"
      },
      {
        "key": "percentile",
        "label": "Parallel percentile (0–1)",
        "ko": "병렬분석 백분위 (0~1)",
        "type": "number",
        "default": "0.95"
      }
    ],
    "formHelp": "Select numeric item columns. Extraction: pca (principal components, default) or pa (principal axis). Enter a factor count or parallel for automatic selection. Rotations: varimax, none, promax (power 4) or oblimin (delta 0). Arguments: data,count,rotation,extraction,parallel simulations,seed,percentile. Parallel simulations: 0 = off; automatic selection defaults to 100, percentile 0.95. Reports KMO, Bartlett, communalities, pattern and structure loadings, factor correlations and regression scores. Principal axis uses SMC-reduced eigenvalues for parallel analysis; PCA uses correlation eigenvalues. For correlated factors, interpret communalities with the factor correlations.",
    "formHelpKo": "숫자 문항 열을 선택합니다. 추출: 주성분(pca, 기본) 또는 주축요인법(pa). 요인 수 또는 자동 선택 parallel을 입력합니다. 회전: varimax·none·promax(지수 4)·oblimin(delta 0). 인수: 자료,요인 수,회전,추출,병렬분석 횟수,시드,백분위. 횟수 0은 끔, 자동 선택 기본값은 100회·백분위 0.95입니다. KMO·Bartlett·공통성·패턴/구조 적재량·요인 상관·회귀 점수를 제공합니다. 병렬분석은 주축요인법에서 SMC 축소 고유값, 주성분에서 상관 고유값을 사용합니다. 사각회전의 공통성은 요인 상관을 함께 반영해 해석합니다.",
    "exampleRows": [
      [
        "1.987",
        "2.255",
        "1.895",
        "1.448",
        "2.702",
        "2.043"
      ],
      [
        "3.103",
        "3.653",
        "2.588",
        "3.608",
        "3.265",
        "3.866"
      ],
      [
        "5.16",
        "4.181",
        "6.042",
        "4.848",
        "4.128",
        "5.317"
      ],
      [
        "1.995",
        "2.767",
        "1.417",
        "-0.304",
        "-1.529",
        "-0.2"
      ],
      [
        "1.802",
        "1.7",
        "2.56",
        "2.591",
        "4.452",
        "3.681"
      ],
      [
        "5.242",
        "3.886",
        "4.375",
        "5.082",
        "5.152",
        "4.701"
      ],
      [
        "3.057",
        "2.704",
        "2.315",
        "2.034",
        "0.528",
        "0.659"
      ],
      [
        "4.576",
        "5.304",
        "4.688",
        "5.573",
        "4.66",
        "5.626"
      ],
      [
        "5.08",
        "3.257",
        "4.251",
        "3.415",
        "3.076",
        "4.508"
      ],
      [
        "2.502",
        "2.733",
        "2.919",
        "3.058",
        "3.403",
        "2.427"
      ],
      [
        "4.23",
        "3.357",
        "4.235",
        "4.595",
        "5.16",
        "5.999"
      ],
      [
        "2.86",
        "3.515",
        "2.619",
        "0.955",
        "1.766",
        "0.26"
      ],
      [
        "3.16",
        "2.893",
        "2.37",
        "2.341",
        "1.676",
        "2.201"
      ],
      [
        "1.101",
        "3.477",
        "2.018",
        "2.67",
        "2.235",
        "3.664"
      ],
      [
        "3.503",
        "4.223",
        "4.268",
        "2.178",
        "3.518",
        "1.656"
      ],
      [
        "3.623",
        "3.309",
        "4.28",
        "2.605",
        "3.676",
        "3.647"
      ],
      [
        "4.285",
        "5.139",
        "6.064",
        "4.069",
        "4.097",
        "4.629"
      ],
      [
        "3.301",
        "2.767",
        "3.271",
        "2.832",
        "3.249",
        "3.621"
      ],
      [
        "2.857",
        "2.677",
        "2.348",
        "3.272",
        "2.696",
        "3.272"
      ],
      [
        "0.449",
        "1.323",
        "0.652",
        "3.013",
        "3.114",
        "1.652"
      ],
      [
        "3.447",
        "3.14",
        "2.42",
        "2.311",
        "3.202",
        "2.063"
      ],
      [
        "2.401",
        "2.196",
        "3.998",
        "1.025",
        "2.349",
        "1.691"
      ],
      [
        "3.54",
        "5.121",
        "3.878",
        "2.99",
        "2.849",
        "3.135"
      ],
      [
        "3.67",
        "4.081",
        "4.264",
        "3.472",
        "3.458",
        "4.419"
      ],
      [
        "3.341",
        "3.341",
        "2.722",
        "2.088",
        "2.846",
        "3.371"
      ],
      [
        "2.491",
        "2.816",
        "1.44",
        "1.192",
        "2.551",
        "1.975"
      ],
      [
        "3.002",
        "2.66",
        "3.754",
        "3.345",
        "5.413",
        "3.962"
      ],
      [
        "2.893",
        "3.409",
        "3.316",
        "3.336",
        "3.614",
        "4.426"
      ],
      [
        "1.843",
        "1.939",
        "2.024",
        "5.012",
        "4.963",
        "5.395"
      ],
      [
        "4.404",
        "3.051",
        "4.195",
        "4.157",
        "2.983",
        "3.546"
      ],
      [
        "3.252",
        "2.804",
        "3.346",
        "2.695",
        "4.096",
        "3.254"
      ],
      [
        "3.384",
        "3.477",
        "4.064",
        "1.625",
        "1.051",
        "0.949"
      ],
      [
        "3.04",
        "2.869",
        "2.962",
        "2.556",
        "1.997",
        "0.53"
      ],
      [
        "2.398",
        "2.777",
        "2.227",
        "3.809",
        "3.915",
        "4.636"
      ],
      [
        "3.253",
        "3.538",
        "4.357",
        "3.697",
        "3.035",
        "3.881"
      ],
      [
        "2.896",
        "2.958",
        "0.826",
        "0.678",
        "1.802",
        "0.825"
      ],
      [
        "1.423",
        "1.154",
        "1.884",
        "3.063",
        "3.992",
        "3.001"
      ],
      [
        "3.389",
        "3.692",
        "2.134",
        "3.7",
        "4.094",
        "4.75"
      ],
      [
        "2.75",
        "3.546",
        "3.742",
        "3.853",
        "3.204",
        "5.14"
      ],
      [
        "3.966",
        "2.911",
        "2.755",
        "2.152",
        "3.222",
        "1.657"
      ],
      [
        "2.267",
        "1.875",
        "2.087",
        "1.343",
        "3.21",
        "3.064"
      ],
      [
        "4.436",
        "4.095",
        "5.016",
        "4.363",
        "3.93",
        "4.141"
      ],
      [
        "-0.224",
        "1.681",
        "0.508",
        "1.922",
        "1.822",
        "2.534"
      ],
      [
        "2.844",
        "2.896",
        "3.813",
        "4.436",
        "2.472",
        "3.68"
      ],
      [
        "2.548",
        "3.279",
        "1.79",
        "2.175",
        "1.88",
        "1.092"
      ],
      [
        "2.624",
        "2.491",
        "2.819",
        "3.493",
        "3.617",
        "2.98"
      ],
      [
        "3.027",
        "3.869",
        "2.073",
        "2.693",
        "2.873",
        "3.391"
      ],
      [
        "2.524",
        "3.183",
        "1.987",
        "3.333",
        "2.888",
        "3.225"
      ]
    ]
  },
  {
    "id": "cfa",
    "label": "Confirmatory factor analysis (CFA)",
    "ko": "확인적 요인분석 (CFA)",
    "input": "table",
    "suffix": ",[1,1,1,2,2,2]",
    "example": "cfa([[1.987,2.255,1.895,1.448,2.702,2.043],[3.103,3.653,2.588,3.608,3.265,3.866],[5.16,4.181,6.042,4.848,4.128,5.317],[1.995,2.767,1.417,-0.304,-1.529,-0.2],[1.802,1.7,2.56,2.591,4.452,3.681],[5.242,3.886,4.375,5.082,5.152,4.701],[3.057,2.704,2.315,2.034,0.528,0.659],[4.576,5.304,4.688,5.573,4.66,5.626],[5.08,3.257,4.251,3.415,3.076,4.508],[2.502,2.733,2.919,3.058,3.403,2.427],[4.23,3.357,4.235,4.595,5.16,5.999],[2.86,3.515,2.619,0.955,1.766,0.26],[3.16,2.893,2.37,2.341,1.676,2.201],[1.101,3.477,2.018,2.67,2.235,3.664],[3.503,4.223,4.268,2.178,3.518,1.656],[3.623,3.309,4.28,2.605,3.676,3.647],[4.285,5.139,6.064,4.069,4.097,4.629],[3.301,2.767,3.271,2.832,3.249,3.621],[2.857,2.677,2.348,3.272,2.696,3.272],[0.449,1.323,0.652,3.013,3.114,1.652],[3.447,3.14,2.42,2.311,3.202,2.063],[2.401,2.196,3.998,1.025,2.349,1.691],[3.54,5.121,3.878,2.99,2.849,3.135],[3.67,4.081,4.264,3.472,3.458,4.419],[3.341,3.341,2.722,2.088,2.846,3.371],[2.491,2.816,1.44,1.192,2.551,1.975],[3.002,2.66,3.754,3.345,5.413,3.962],[2.893,3.409,3.316,3.336,3.614,4.426],[1.843,1.939,2.024,5.012,4.963,5.395],[4.404,3.051,4.195,4.157,2.983,3.546],[3.252,2.804,3.346,2.695,4.096,3.254],[3.384,3.477,4.064,1.625,1.051,0.949],[3.04,2.869,2.962,2.556,1.997,0.53],[2.398,2.777,2.227,3.809,3.915,4.636],[3.253,3.538,4.357,3.697,3.035,3.881],[2.896,2.958,0.826,0.678,1.802,0.825],[1.423,1.154,1.884,3.063,3.992,3.001],[3.389,3.692,2.134,3.7,4.094,4.75],[2.75,3.546,3.742,3.853,3.204,5.14],[3.966,2.911,2.755,2.152,3.222,1.657],[2.267,1.875,2.087,1.343,3.21,3.064],[4.436,4.095,5.016,4.363,3.93,4.141],[-0.224,1.681,0.508,1.922,1.822,2.534],[2.844,2.896,3.813,4.436,2.472,3.68],[2.548,3.279,1.79,2.175,1.88,1.092],[2.624,2.491,2.819,3.493,3.617,2.98],[3.027,3.869,2.073,2.693,2.873,3.391],[2.524,3.183,1.987,3.333,2.888,3.225]],[1,1,1,2,2,2])",
    "help": "Choose ML for continuous indicators or WLSMV for numeric ordinal category codes (ordered numerically). At least three primary indicators per factor; first loading fixed at 1. Cross-loadings use [indicator,factor] pairs on non-marker indicators. ML supports complete rows or FIML under MCAR/MAR. WLSMV requires complete rows and uses probit thresholds/polychoric correlations, DWLS, full influence-covariance sandwich SEs and scaled-shifted mean/variance-adjusted T3. Measurement invariance: configural (group free), metric (equal loadings), scalar (also equal intercepts for ML or response thresholds for WLSMV), strict (also equal residual variances). Scalar/strict estimate other-group latent means, fixing reference means to zero. Multi-group scalar/strict WLSMV requires the same observed categories in each group, at least three per indicator; reference residual variances are 1, scalar frees them in other groups and strict fixes all to 1. Reports chi-square, df, p, CFI, TLI, RMSEA and complete-data SRMR. WLSMV fit indices use adjusted tests; adjusted chi-squares cannot be subtracted for difference testing. Arguments: data,factors,cross-loadings,complete/fiml,group IDs,configural/metric/scalar/strict,ml/wlsmv.",
    "helpKo": "연속형 지표는 ML, 숫자 범주 코드의 서열형 지표는 WLSMV를 선택합니다(범주 순서는 숫자순). 요인당 주 지표 최소 3개, 첫 적재량은 1로 고정합니다. 교차적재는 첫 지표를 제외한 [지표,요인] 쌍입니다. ML은 완전자료 또는 MCAR/MAR 가정의 FIML, WLSMV는 완전자료를 사용합니다. WLSMV: probit 역치·다분상관, DWLS, 전체 영향함수 공분산의 샌드위치 표준오차, 평균·분산 보정 T3 검정. 측정동일성: configural(집단별 자유), metric(적재량 동일), scalar(ML은 절편, WLSMV는 반응 역치도 동일), strict(잔차분산도 동일). scalar/strict는 기준 집단 잠재평균을 0으로 고정하고 다른 집단 잠재평균을 추정합니다. 다집단 scalar/strict WLSMV는 지표마다 집단 간 같은 관측 범주가 3개 이상 필요합니다. 기준 잔차분산은 1, scalar는 다른 집단에서 자유 추정, strict는 모두 1입니다. χ²·df·p·CFI·TLI·RMSEA·완전자료 SRMR을 제공합니다. WLSMV 적합도는 보정 검정 기준이며 보정 χ²를 단순 차감한 차이검정은 사용할 수 없습니다. 인수: 자료,요인,교차적재,complete/fiml,집단 ID,configural/metric/scalar/strict,ml/wlsmv.",
    "section": "advanced",
    "group": "Measurement & structural models",
    "groupKo": "측정·구조 모형",
    "controls": [
      {
        "key": "columns",
        "label": "Feature / item columns",
        "ko": "변수·문항 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "factors",
        "label": "Primary factor IDs in selected order",
        "ko": "선택 순서의 주 요인 ID",
        "type": "text",
        "default": "1,1,1,2,2,2"
      },
      {
        "key": "cross",
        "label": "Cross-loadings: indicator,factor;…",
        "ko": "교차적재: 선택 지표 번호,요인;…",
        "type": "text",
        "default": ""
      },
      {
        "key": "estimator",
        "label": "Estimator",
        "ko": "추정 방식",
        "type": "choice",
        "default": "ml",
        "choices": [
          {
            "id": "ml",
            "label": "ML (continuous)",
            "ko": "ML (연속형)"
          },
          {
            "id": "wlsmv",
            "label": "WLSMV (ordinal)",
            "ko": "WLSMV (서열형)"
          }
        ]
      },
      {
        "key": "missing",
        "label": "Missing data",
        "ko": "결측 자료",
        "type": "choice",
        "default": "complete",
        "choices": [
          {
            "id": "complete",
            "label": "Complete rows required",
            "ko": "완전한 행 필요"
          },
          {
            "id": "fiml",
            "label": "FIML (MAR, continuous)",
            "ko": "FIML (MAR, 연속형)"
          }
        ],
        "when": {
          "estimator": [
            "ml"
          ]
        }
      },
      {
        "key": "groupMode",
        "label": "Groups",
        "ko": "집단",
        "type": "choice",
        "default": "single",
        "choices": [
          {
            "id": "single",
            "label": "Single group",
            "ko": "단일 집단"
          },
          {
            "id": "multi",
            "label": "Multiple groups",
            "ko": "다집단"
          }
        ]
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "집단 열",
        "type": "column",
        "default": 0,
        "when": {
          "groupMode": [
            "multi"
          ]
        }
      },
      {
        "key": "invariance",
        "label": "Measurement invariance",
        "ko": "측정동일성",
        "type": "choice",
        "default": "configural",
        "choices": [
          {
            "id": "configural",
            "label": "Configural (group free)",
            "ko": "형태 (집단별 자유)"
          },
          {
            "id": "metric",
            "label": "Metric (equal loadings)",
            "ko": "측정 (적재량 동일)"
          },
          {
            "id": "scalar",
            "label": "Scalar (equal loadings & intercepts/thresholds)",
            "ko": "Scalar (적재량·절편/역치 동일)"
          },
          {
            "id": "strict",
            "label": "Strict (scalar & equal residual variances)",
            "ko": "Strict (scalar·잔차분산 동일)"
          }
        ],
        "when": {
          "groupMode": [
            "multi"
          ]
        }
      }
    ],
    "formHelp": "Choose ML for continuous indicators or WLSMV for numeric ordinal category codes (ordered numerically). At least three primary indicators per factor; first loading fixed at 1. Cross-loadings use [indicator,factor] pairs on non-marker indicators. ML supports complete rows or FIML under MCAR/MAR. WLSMV requires complete rows and uses probit thresholds/polychoric correlations, DWLS, full influence-covariance sandwich SEs and scaled-shifted mean/variance-adjusted T3. Measurement invariance: configural (group free), metric (equal loadings), scalar (also equal intercepts for ML or response thresholds for WLSMV), strict (also equal residual variances). Scalar/strict estimate other-group latent means, fixing reference means to zero. Multi-group scalar/strict WLSMV requires the same observed categories in each group, at least three per indicator; reference residual variances are 1, scalar frees them in other groups and strict fixes all to 1. Reports chi-square, df, p, CFI, TLI, RMSEA and complete-data SRMR. WLSMV fit indices use adjusted tests; adjusted chi-squares cannot be subtracted for difference testing. Arguments: data,factors,cross-loadings,complete/fiml,group IDs,configural/metric/scalar/strict,ml/wlsmv.",
    "formHelpKo": "연속형 지표는 ML, 숫자 범주 코드의 서열형 지표는 WLSMV를 선택합니다(범주 순서는 숫자순). 요인당 주 지표 최소 3개, 첫 적재량은 1로 고정합니다. 교차적재는 첫 지표를 제외한 [지표,요인] 쌍입니다. ML은 완전자료 또는 MCAR/MAR 가정의 FIML, WLSMV는 완전자료를 사용합니다. WLSMV: probit 역치·다분상관, DWLS, 전체 영향함수 공분산의 샌드위치 표준오차, 평균·분산 보정 T3 검정. 측정동일성: configural(집단별 자유), metric(적재량 동일), scalar(ML은 절편, WLSMV는 반응 역치도 동일), strict(잔차분산도 동일). scalar/strict는 기준 집단 잠재평균을 0으로 고정하고 다른 집단 잠재평균을 추정합니다. 다집단 scalar/strict WLSMV는 지표마다 집단 간 같은 관측 범주가 3개 이상 필요합니다. 기준 잔차분산은 1, scalar는 다른 집단에서 자유 추정, strict는 모두 1입니다. χ²·df·p·CFI·TLI·RMSEA·완전자료 SRMR을 제공합니다. WLSMV 적합도는 보정 검정 기준이며 보정 χ²를 단순 차감한 차이검정은 사용할 수 없습니다. 인수: 자료,요인,교차적재,complete/fiml,집단 ID,configural/metric/scalar/strict,ml/wlsmv.",
    "exampleRows": [
      [
        "1.987",
        "2.255",
        "1.895",
        "1.448",
        "2.702",
        "2.043"
      ],
      [
        "3.103",
        "3.653",
        "2.588",
        "3.608",
        "3.265",
        "3.866"
      ],
      [
        "5.16",
        "4.181",
        "6.042",
        "4.848",
        "4.128",
        "5.317"
      ],
      [
        "1.995",
        "2.767",
        "1.417",
        "-0.304",
        "-1.529",
        "-0.2"
      ],
      [
        "1.802",
        "1.7",
        "2.56",
        "2.591",
        "4.452",
        "3.681"
      ],
      [
        "5.242",
        "3.886",
        "4.375",
        "5.082",
        "5.152",
        "4.701"
      ],
      [
        "3.057",
        "2.704",
        "2.315",
        "2.034",
        "0.528",
        "0.659"
      ],
      [
        "4.576",
        "5.304",
        "4.688",
        "5.573",
        "4.66",
        "5.626"
      ],
      [
        "5.08",
        "3.257",
        "4.251",
        "3.415",
        "3.076",
        "4.508"
      ],
      [
        "2.502",
        "2.733",
        "2.919",
        "3.058",
        "3.403",
        "2.427"
      ],
      [
        "4.23",
        "3.357",
        "4.235",
        "4.595",
        "5.16",
        "5.999"
      ],
      [
        "2.86",
        "3.515",
        "2.619",
        "0.955",
        "1.766",
        "0.26"
      ],
      [
        "3.16",
        "2.893",
        "2.37",
        "2.341",
        "1.676",
        "2.201"
      ],
      [
        "1.101",
        "3.477",
        "2.018",
        "2.67",
        "2.235",
        "3.664"
      ],
      [
        "3.503",
        "4.223",
        "4.268",
        "2.178",
        "3.518",
        "1.656"
      ],
      [
        "3.623",
        "3.309",
        "4.28",
        "2.605",
        "3.676",
        "3.647"
      ],
      [
        "4.285",
        "5.139",
        "6.064",
        "4.069",
        "4.097",
        "4.629"
      ],
      [
        "3.301",
        "2.767",
        "3.271",
        "2.832",
        "3.249",
        "3.621"
      ],
      [
        "2.857",
        "2.677",
        "2.348",
        "3.272",
        "2.696",
        "3.272"
      ],
      [
        "0.449",
        "1.323",
        "0.652",
        "3.013",
        "3.114",
        "1.652"
      ],
      [
        "3.447",
        "3.14",
        "2.42",
        "2.311",
        "3.202",
        "2.063"
      ],
      [
        "2.401",
        "2.196",
        "3.998",
        "1.025",
        "2.349",
        "1.691"
      ],
      [
        "3.54",
        "5.121",
        "3.878",
        "2.99",
        "2.849",
        "3.135"
      ],
      [
        "3.67",
        "4.081",
        "4.264",
        "3.472",
        "3.458",
        "4.419"
      ],
      [
        "3.341",
        "3.341",
        "2.722",
        "2.088",
        "2.846",
        "3.371"
      ],
      [
        "2.491",
        "2.816",
        "1.44",
        "1.192",
        "2.551",
        "1.975"
      ],
      [
        "3.002",
        "2.66",
        "3.754",
        "3.345",
        "5.413",
        "3.962"
      ],
      [
        "2.893",
        "3.409",
        "3.316",
        "3.336",
        "3.614",
        "4.426"
      ],
      [
        "1.843",
        "1.939",
        "2.024",
        "5.012",
        "4.963",
        "5.395"
      ],
      [
        "4.404",
        "3.051",
        "4.195",
        "4.157",
        "2.983",
        "3.546"
      ],
      [
        "3.252",
        "2.804",
        "3.346",
        "2.695",
        "4.096",
        "3.254"
      ],
      [
        "3.384",
        "3.477",
        "4.064",
        "1.625",
        "1.051",
        "0.949"
      ],
      [
        "3.04",
        "2.869",
        "2.962",
        "2.556",
        "1.997",
        "0.53"
      ],
      [
        "2.398",
        "2.777",
        "2.227",
        "3.809",
        "3.915",
        "4.636"
      ],
      [
        "3.253",
        "3.538",
        "4.357",
        "3.697",
        "3.035",
        "3.881"
      ],
      [
        "2.896",
        "2.958",
        "0.826",
        "0.678",
        "1.802",
        "0.825"
      ],
      [
        "1.423",
        "1.154",
        "1.884",
        "3.063",
        "3.992",
        "3.001"
      ],
      [
        "3.389",
        "3.692",
        "2.134",
        "3.7",
        "4.094",
        "4.75"
      ],
      [
        "2.75",
        "3.546",
        "3.742",
        "3.853",
        "3.204",
        "5.14"
      ],
      [
        "3.966",
        "2.911",
        "2.755",
        "2.152",
        "3.222",
        "1.657"
      ],
      [
        "2.267",
        "1.875",
        "2.087",
        "1.343",
        "3.21",
        "3.064"
      ],
      [
        "4.436",
        "4.095",
        "5.016",
        "4.363",
        "3.93",
        "4.141"
      ],
      [
        "-0.224",
        "1.681",
        "0.508",
        "1.922",
        "1.822",
        "2.534"
      ],
      [
        "2.844",
        "2.896",
        "3.813",
        "4.436",
        "2.472",
        "3.68"
      ],
      [
        "2.548",
        "3.279",
        "1.79",
        "2.175",
        "1.88",
        "1.092"
      ],
      [
        "2.624",
        "2.491",
        "2.819",
        "3.493",
        "3.617",
        "2.98"
      ],
      [
        "3.027",
        "3.869",
        "2.073",
        "2.693",
        "2.873",
        "3.391"
      ],
      [
        "2.524",
        "3.183",
        "1.987",
        "3.333",
        "2.888",
        "3.225"
      ]
    ],
    "ordinalExampleCuts": [
      2,
      3,
      4
    ],
    "multiGroupExampleIds": [
      "1",
      "2"
    ]
  },
  {
    "id": "sem",
    "label": "Structural equation model (SEM)",
    "ko": "구조방정식 모형 (SEM)",
    "input": "table",
    "suffix": ",[1,1,1,2,2,2],[[1,2]]",
    "example": "sem([[1.987,2.255,1.895,1.448,2.702,2.043],[3.103,3.653,2.588,3.608,3.265,3.866],[5.16,4.181,6.042,4.848,4.128,5.317],[1.995,2.767,1.417,-0.304,-1.529,-0.2],[1.802,1.7,2.56,2.591,4.452,3.681],[5.242,3.886,4.375,5.082,5.152,4.701],[3.057,2.704,2.315,2.034,0.528,0.659],[4.576,5.304,4.688,5.573,4.66,5.626],[5.08,3.257,4.251,3.415,3.076,4.508],[2.502,2.733,2.919,3.058,3.403,2.427],[4.23,3.357,4.235,4.595,5.16,5.999],[2.86,3.515,2.619,0.955,1.766,0.26],[3.16,2.893,2.37,2.341,1.676,2.201],[1.101,3.477,2.018,2.67,2.235,3.664],[3.503,4.223,4.268,2.178,3.518,1.656],[3.623,3.309,4.28,2.605,3.676,3.647],[4.285,5.139,6.064,4.069,4.097,4.629],[3.301,2.767,3.271,2.832,3.249,3.621],[2.857,2.677,2.348,3.272,2.696,3.272],[0.449,1.323,0.652,3.013,3.114,1.652],[3.447,3.14,2.42,2.311,3.202,2.063],[2.401,2.196,3.998,1.025,2.349,1.691],[3.54,5.121,3.878,2.99,2.849,3.135],[3.67,4.081,4.264,3.472,3.458,4.419],[3.341,3.341,2.722,2.088,2.846,3.371],[2.491,2.816,1.44,1.192,2.551,1.975],[3.002,2.66,3.754,3.345,5.413,3.962],[2.893,3.409,3.316,3.336,3.614,4.426],[1.843,1.939,2.024,5.012,4.963,5.395],[4.404,3.051,4.195,4.157,2.983,3.546],[3.252,2.804,3.346,2.695,4.096,3.254],[3.384,3.477,4.064,1.625,1.051,0.949],[3.04,2.869,2.962,2.556,1.997,0.53],[2.398,2.777,2.227,3.809,3.915,4.636],[3.253,3.538,4.357,3.697,3.035,3.881],[2.896,2.958,0.826,0.678,1.802,0.825],[1.423,1.154,1.884,3.063,3.992,3.001],[3.389,3.692,2.134,3.7,4.094,4.75],[2.75,3.546,3.742,3.853,3.204,5.14],[3.966,2.911,2.755,2.152,3.222,1.657],[2.267,1.875,2.087,1.343,3.21,3.064],[4.436,4.095,5.016,4.363,3.93,4.141],[-0.224,1.681,0.508,1.922,1.822,2.534],[2.844,2.896,3.813,4.436,2.472,3.68],[2.548,3.279,1.79,2.175,1.88,1.092],[2.624,2.491,2.819,3.493,3.617,2.98],[3.027,3.869,2.073,2.693,2.873,3.391],[2.524,3.183,1.987,3.333,2.888,3.225]],[1,1,1,2,2,2],[[1,2]])",
    "help": "Choose ML for continuous indicators or WLSMV for numeric ordinal category codes (ordered numerically). At least three primary indicators per factor; first loading fixed at 1. Cross-loadings use [indicator,factor] pairs on non-marker indicators. ML supports complete rows or FIML under MCAR/MAR. WLSMV requires complete rows and uses probit thresholds/polychoric correlations, DWLS, full influence-covariance sandwich SEs and scaled-shifted mean/variance-adjusted T3. Measurement invariance: configural (group free), metric (equal loadings), scalar (also equal intercepts for ML or response thresholds for WLSMV), strict (also equal residual variances). Scalar/strict estimate other-group latent means, fixing reference means to zero. Multi-group scalar/strict WLSMV requires the same observed categories in each group, at least three per indicator; reference residual variances are 1, scalar frees them in other groups and strict fixes all to 1. Reports chi-square, df, p, CFI, TLI, RMSEA and complete-data SRMR. WLSMV fit indices use adjusted tests; adjusted chi-squares cannot be subtracted for difference testing. Add acyclic latent paths [source,target]; exogenous factors may covary, endogenous disturbances and indicator errors are independent. Arguments: data,factors,paths,cross-loadings,complete/fiml,group IDs,configural/metric/scalar/strict,ml/wlsmv.",
    "helpKo": "연속형 지표는 ML, 숫자 범주 코드의 서열형 지표는 WLSMV를 선택합니다(범주 순서는 숫자순). 요인당 주 지표 최소 3개, 첫 적재량은 1로 고정합니다. 교차적재는 첫 지표를 제외한 [지표,요인] 쌍입니다. ML은 완전자료 또는 MCAR/MAR 가정의 FIML, WLSMV는 완전자료를 사용합니다. WLSMV: probit 역치·다분상관, DWLS, 전체 영향함수 공분산의 샌드위치 표준오차, 평균·분산 보정 T3 검정. 측정동일성: configural(집단별 자유), metric(적재량 동일), scalar(ML은 절편, WLSMV는 반응 역치도 동일), strict(잔차분산도 동일). scalar/strict는 기준 집단 잠재평균을 0으로 고정하고 다른 집단 잠재평균을 추정합니다. 다집단 scalar/strict WLSMV는 지표마다 집단 간 같은 관측 범주가 3개 이상 필요합니다. 기준 잔차분산은 1, scalar는 다른 집단에서 자유 추정, strict는 모두 1입니다. χ²·df·p·CFI·TLI·RMSEA·완전자료 SRMR을 제공합니다. WLSMV 적합도는 보정 검정 기준이며 보정 χ²를 단순 차감한 차이검정은 사용할 수 없습니다. 비순환 잠재 경로 [출발,도착]를 지정합니다. 외생 요인 간 상관, 독립 내생 교란·지표 오차를 가정합니다. 인수: 자료,요인,경로,교차적재,complete/fiml,집단 ID,configural/metric/scalar/strict,ml/wlsmv.",
    "section": "advanced",
    "group": "Measurement & structural models",
    "groupKo": "측정·구조 모형",
    "controls": [
      {
        "key": "columns",
        "label": "Feature / item columns",
        "ko": "변수·문항 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "factors",
        "label": "Primary factor IDs in selected order",
        "ko": "선택 순서의 주 요인 ID",
        "type": "text",
        "default": "1,1,1,2,2,2"
      },
      {
        "key": "cross",
        "label": "Cross-loadings: indicator,factor;…",
        "ko": "교차적재: 선택 지표 번호,요인;…",
        "type": "text",
        "default": ""
      },
      {
        "key": "estimator",
        "label": "Estimator",
        "ko": "추정 방식",
        "type": "choice",
        "default": "ml",
        "choices": [
          {
            "id": "ml",
            "label": "ML (continuous)",
            "ko": "ML (연속형)"
          },
          {
            "id": "wlsmv",
            "label": "WLSMV (ordinal)",
            "ko": "WLSMV (서열형)"
          }
        ]
      },
      {
        "key": "missing",
        "label": "Missing data",
        "ko": "결측 자료",
        "type": "choice",
        "default": "complete",
        "choices": [
          {
            "id": "complete",
            "label": "Complete rows required",
            "ko": "완전한 행 필요"
          },
          {
            "id": "fiml",
            "label": "FIML (MAR, continuous)",
            "ko": "FIML (MAR, 연속형)"
          }
        ],
        "when": {
          "estimator": [
            "ml"
          ]
        }
      },
      {
        "key": "groupMode",
        "label": "Groups",
        "ko": "집단",
        "type": "choice",
        "default": "single",
        "choices": [
          {
            "id": "single",
            "label": "Single group",
            "ko": "단일 집단"
          },
          {
            "id": "multi",
            "label": "Multiple groups",
            "ko": "다집단"
          }
        ]
      },
      {
        "key": "group",
        "label": "Group column",
        "ko": "집단 열",
        "type": "column",
        "default": 0,
        "when": {
          "groupMode": [
            "multi"
          ]
        }
      },
      {
        "key": "invariance",
        "label": "Measurement invariance",
        "ko": "측정동일성",
        "type": "choice",
        "default": "configural",
        "choices": [
          {
            "id": "configural",
            "label": "Configural (group free)",
            "ko": "형태 (집단별 자유)"
          },
          {
            "id": "metric",
            "label": "Metric (equal loadings)",
            "ko": "측정 (적재량 동일)"
          },
          {
            "id": "scalar",
            "label": "Scalar (equal loadings & intercepts/thresholds)",
            "ko": "Scalar (적재량·절편/역치 동일)"
          },
          {
            "id": "strict",
            "label": "Strict (scalar & equal residual variances)",
            "ko": "Strict (scalar·잔차분산 동일)"
          }
        ],
        "when": {
          "groupMode": [
            "multi"
          ]
        }
      },
      {
        "key": "paths",
        "label": "Latent paths: source,target;…",
        "ko": "잠재 경로: 출발,도착;…",
        "type": "text",
        "default": "1,2"
      }
    ],
    "formHelp": "Choose ML for continuous indicators or WLSMV for numeric ordinal category codes (ordered numerically). At least three primary indicators per factor; first loading fixed at 1. Cross-loadings use [indicator,factor] pairs on non-marker indicators. ML supports complete rows or FIML under MCAR/MAR. WLSMV requires complete rows and uses probit thresholds/polychoric correlations, DWLS, full influence-covariance sandwich SEs and scaled-shifted mean/variance-adjusted T3. Measurement invariance: configural (group free), metric (equal loadings), scalar (also equal intercepts for ML or response thresholds for WLSMV), strict (also equal residual variances). Scalar/strict estimate other-group latent means, fixing reference means to zero. Multi-group scalar/strict WLSMV requires the same observed categories in each group, at least three per indicator; reference residual variances are 1, scalar frees them in other groups and strict fixes all to 1. Reports chi-square, df, p, CFI, TLI, RMSEA and complete-data SRMR. WLSMV fit indices use adjusted tests; adjusted chi-squares cannot be subtracted for difference testing. Add acyclic latent paths [source,target]; exogenous factors may covary, endogenous disturbances and indicator errors are independent. Arguments: data,factors,paths,cross-loadings,complete/fiml,group IDs,configural/metric/scalar/strict,ml/wlsmv.",
    "formHelpKo": "연속형 지표는 ML, 숫자 범주 코드의 서열형 지표는 WLSMV를 선택합니다(범주 순서는 숫자순). 요인당 주 지표 최소 3개, 첫 적재량은 1로 고정합니다. 교차적재는 첫 지표를 제외한 [지표,요인] 쌍입니다. ML은 완전자료 또는 MCAR/MAR 가정의 FIML, WLSMV는 완전자료를 사용합니다. WLSMV: probit 역치·다분상관, DWLS, 전체 영향함수 공분산의 샌드위치 표준오차, 평균·분산 보정 T3 검정. 측정동일성: configural(집단별 자유), metric(적재량 동일), scalar(ML은 절편, WLSMV는 반응 역치도 동일), strict(잔차분산도 동일). scalar/strict는 기준 집단 잠재평균을 0으로 고정하고 다른 집단 잠재평균을 추정합니다. 다집단 scalar/strict WLSMV는 지표마다 집단 간 같은 관측 범주가 3개 이상 필요합니다. 기준 잔차분산은 1, scalar는 다른 집단에서 자유 추정, strict는 모두 1입니다. χ²·df·p·CFI·TLI·RMSEA·완전자료 SRMR을 제공합니다. WLSMV 적합도는 보정 검정 기준이며 보정 χ²를 단순 차감한 차이검정은 사용할 수 없습니다. 비순환 잠재 경로 [출발,도착]를 지정합니다. 외생 요인 간 상관, 독립 내생 교란·지표 오차를 가정합니다. 인수: 자료,요인,경로,교차적재,complete/fiml,집단 ID,configural/metric/scalar/strict,ml/wlsmv.",
    "exampleRows": [
      [
        "1.987",
        "2.255",
        "1.895",
        "1.448",
        "2.702",
        "2.043"
      ],
      [
        "3.103",
        "3.653",
        "2.588",
        "3.608",
        "3.265",
        "3.866"
      ],
      [
        "5.16",
        "4.181",
        "6.042",
        "4.848",
        "4.128",
        "5.317"
      ],
      [
        "1.995",
        "2.767",
        "1.417",
        "-0.304",
        "-1.529",
        "-0.2"
      ],
      [
        "1.802",
        "1.7",
        "2.56",
        "2.591",
        "4.452",
        "3.681"
      ],
      [
        "5.242",
        "3.886",
        "4.375",
        "5.082",
        "5.152",
        "4.701"
      ],
      [
        "3.057",
        "2.704",
        "2.315",
        "2.034",
        "0.528",
        "0.659"
      ],
      [
        "4.576",
        "5.304",
        "4.688",
        "5.573",
        "4.66",
        "5.626"
      ],
      [
        "5.08",
        "3.257",
        "4.251",
        "3.415",
        "3.076",
        "4.508"
      ],
      [
        "2.502",
        "2.733",
        "2.919",
        "3.058",
        "3.403",
        "2.427"
      ],
      [
        "4.23",
        "3.357",
        "4.235",
        "4.595",
        "5.16",
        "5.999"
      ],
      [
        "2.86",
        "3.515",
        "2.619",
        "0.955",
        "1.766",
        "0.26"
      ],
      [
        "3.16",
        "2.893",
        "2.37",
        "2.341",
        "1.676",
        "2.201"
      ],
      [
        "1.101",
        "3.477",
        "2.018",
        "2.67",
        "2.235",
        "3.664"
      ],
      [
        "3.503",
        "4.223",
        "4.268",
        "2.178",
        "3.518",
        "1.656"
      ],
      [
        "3.623",
        "3.309",
        "4.28",
        "2.605",
        "3.676",
        "3.647"
      ],
      [
        "4.285",
        "5.139",
        "6.064",
        "4.069",
        "4.097",
        "4.629"
      ],
      [
        "3.301",
        "2.767",
        "3.271",
        "2.832",
        "3.249",
        "3.621"
      ],
      [
        "2.857",
        "2.677",
        "2.348",
        "3.272",
        "2.696",
        "3.272"
      ],
      [
        "0.449",
        "1.323",
        "0.652",
        "3.013",
        "3.114",
        "1.652"
      ],
      [
        "3.447",
        "3.14",
        "2.42",
        "2.311",
        "3.202",
        "2.063"
      ],
      [
        "2.401",
        "2.196",
        "3.998",
        "1.025",
        "2.349",
        "1.691"
      ],
      [
        "3.54",
        "5.121",
        "3.878",
        "2.99",
        "2.849",
        "3.135"
      ],
      [
        "3.67",
        "4.081",
        "4.264",
        "3.472",
        "3.458",
        "4.419"
      ],
      [
        "3.341",
        "3.341",
        "2.722",
        "2.088",
        "2.846",
        "3.371"
      ],
      [
        "2.491",
        "2.816",
        "1.44",
        "1.192",
        "2.551",
        "1.975"
      ],
      [
        "3.002",
        "2.66",
        "3.754",
        "3.345",
        "5.413",
        "3.962"
      ],
      [
        "2.893",
        "3.409",
        "3.316",
        "3.336",
        "3.614",
        "4.426"
      ],
      [
        "1.843",
        "1.939",
        "2.024",
        "5.012",
        "4.963",
        "5.395"
      ],
      [
        "4.404",
        "3.051",
        "4.195",
        "4.157",
        "2.983",
        "3.546"
      ],
      [
        "3.252",
        "2.804",
        "3.346",
        "2.695",
        "4.096",
        "3.254"
      ],
      [
        "3.384",
        "3.477",
        "4.064",
        "1.625",
        "1.051",
        "0.949"
      ],
      [
        "3.04",
        "2.869",
        "2.962",
        "2.556",
        "1.997",
        "0.53"
      ],
      [
        "2.398",
        "2.777",
        "2.227",
        "3.809",
        "3.915",
        "4.636"
      ],
      [
        "3.253",
        "3.538",
        "4.357",
        "3.697",
        "3.035",
        "3.881"
      ],
      [
        "2.896",
        "2.958",
        "0.826",
        "0.678",
        "1.802",
        "0.825"
      ],
      [
        "1.423",
        "1.154",
        "1.884",
        "3.063",
        "3.992",
        "3.001"
      ],
      [
        "3.389",
        "3.692",
        "2.134",
        "3.7",
        "4.094",
        "4.75"
      ],
      [
        "2.75",
        "3.546",
        "3.742",
        "3.853",
        "3.204",
        "5.14"
      ],
      [
        "3.966",
        "2.911",
        "2.755",
        "2.152",
        "3.222",
        "1.657"
      ],
      [
        "2.267",
        "1.875",
        "2.087",
        "1.343",
        "3.21",
        "3.064"
      ],
      [
        "4.436",
        "4.095",
        "5.016",
        "4.363",
        "3.93",
        "4.141"
      ],
      [
        "-0.224",
        "1.681",
        "0.508",
        "1.922",
        "1.822",
        "2.534"
      ],
      [
        "2.844",
        "2.896",
        "3.813",
        "4.436",
        "2.472",
        "3.68"
      ],
      [
        "2.548",
        "3.279",
        "1.79",
        "2.175",
        "1.88",
        "1.092"
      ],
      [
        "2.624",
        "2.491",
        "2.819",
        "3.493",
        "3.617",
        "2.98"
      ],
      [
        "3.027",
        "3.869",
        "2.073",
        "2.693",
        "2.873",
        "3.391"
      ],
      [
        "2.524",
        "3.183",
        "1.987",
        "3.333",
        "2.888",
        "3.225"
      ]
    ],
    "ordinalExampleCuts": [
      2,
      3,
      4
    ],
    "multiGroupExampleIds": [
      "1",
      "2"
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
    "id": "discriminantanalysis",
    "label": "Discriminant analysis (LDA/QDA)",
    "ko": "판별분석 (LDA/QDA)",
    "input": "table",
    "suffix": ",lda,empirical",
    "example": "discriminantanalysis([[1,2,1],[2,1,1],[1,1,1],[2,3,1],[4,5,2],[5,4,2],[4,4,2],[5,6,2]],lda,empirical)",
    "help": "Rows: features, class. LDA pooled / QDA separate unbiased covariances; empirical/equal priors. Optional fourth argument: new feature rows. Training confusion and accuracy use the fitted sample.",
    "helpKo": "열: 변수·분류. LDA 합동 / QDA 개별 불편 공분산; 경험·동일 사전확률. 선택적 넷째 인수: 새 변수 행. 학습 혼동표·정확도는 적합에 사용한 표본 기준입니다.",
    "section": "advanced",
    "group": "Multivariate analysis",
    "groupKo": "다변량 분석",
    "controls": [
      {
        "key": "response",
        "label": "Class",
        "ko": "분류 열",
        "type": "column",
        "default": -1
      },
      {
        "key": "predictors",
        "label": "Features",
        "ko": "변수 열",
        "type": "columns",
        "default": "auto"
      },
      {
        "key": "method",
        "label": "Method",
        "ko": "방법",
        "type": "choice",
        "default": "lda",
        "choices": [
          {
            "id": "lda",
            "label": "LDA",
            "ko": "LDA"
          },
          {
            "id": "qda",
            "label": "QDA",
            "ko": "QDA"
          }
        ]
      },
      {
        "key": "prior",
        "label": "Class priors",
        "ko": "분류 사전확률",
        "type": "choice",
        "default": "empirical",
        "choices": [
          {
            "id": "empirical",
            "label": "Empirical",
            "ko": "경험 빈도"
          },
          {
            "id": "equal",
            "label": "Equal",
            "ko": "동일"
          }
        ]
      }
    ],
    "formHelp": "Rows: features, class. LDA pooled / QDA separate unbiased covariances; empirical/equal priors. Optional fourth argument: new feature rows. Training confusion and accuracy use the fitted sample.",
    "formHelpKo": "열: 변수·분류. LDA 합동 / QDA 개별 불편 공분산; 경험·동일 사전확률. 선택적 넷째 인수: 새 변수 행. 학습 혼동표·정확도는 적합에 사용한 표본 기준입니다.",
    "exampleRows": [
      [
        "1",
        "2",
        "1"
      ],
      [
        "2",
        "1",
        "1"
      ],
      [
        "1",
        "1",
        "1"
      ],
      [
        "2",
        "3",
        "1"
      ],
      [
        "4",
        "5",
        "2"
      ],
      [
        "5",
        "4",
        "2"
      ],
      [
        "4",
        "4",
        "2"
      ],
      [
        "5",
        "6",
        "2"
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
    "id": "hcluster",
    "label": "Hierarchical clustering",
    "ko": "계층적 군집분석",
    "input": "table",
    "suffix": ",2,ward,1",
    "example": "hcluster([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,ward,1)",
    "help": "Independent agglomerative analysis; clusters, single/complete/average/Ward Euclidean linkage, standardize 1/0. Returns all merges, heights, sizes and cut assignments. Leaves 1…n, merge nodes n+1…2n−1.",
    "helpKo": "독립 응집 분석; 군집 수·single/complete/average/Ward 유클리드 연결·표준화 1/0. 전체 병합·거리·크기·절단 배정. 잎 1…n, 병합 노드 n+1…2n−1.",
    "section": "advanced",
    "group": "Multivariate analysis",
    "groupKo": "다변량 분석",
    "controls": [
      {
        "key": "columns",
        "label": "Feature / item columns",
        "ko": "변수·문항 열",
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
        "key": "linkage",
        "label": "Linkage",
        "ko": "연결 방법",
        "type": "choice",
        "default": "ward",
        "choices": [
          {
            "id": "ward",
            "label": "Ward",
            "ko": "Ward"
          },
          {
            "id": "single",
            "label": "Single",
            "ko": "Single"
          },
          {
            "id": "complete",
            "label": "Complete",
            "ko": "Complete"
          },
          {
            "id": "average",
            "label": "Average",
            "ko": "Average"
          }
        ]
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
            "label": "Standardize",
            "ko": "표준화"
          },
          {
            "id": "0",
            "label": "Raw scale",
            "ko": "원척도"
          }
        ]
      }
    ],
    "formHelp": "Independent agglomerative analysis; clusters, single/complete/average/Ward Euclidean linkage, standardize 1/0. Returns all merges, heights, sizes and cut assignments. Leaves 1…n, merge nodes n+1…2n−1.",
    "formHelpKo": "독립 응집 분석; 군집 수·single/complete/average/Ward 유클리드 연결·표준화 1/0. 전체 병합·거리·크기·절단 배정. 잎 1…n, 병합 노드 n+1…2n−1.",
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
