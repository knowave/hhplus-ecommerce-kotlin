#!/bin/bash

# 입력 JSON 읽기
input=$(cat)

# JSON 데이터 추출
MODEL=$(echo "$input" | jq -r '.model.display_name')
INPUT_TOKENS=$(echo "$input" | jq -r '.context_window.total_input_tokens')
OUTPUT_TOKENS=$(echo "$input" | jq -r '.context_window.total_output_tokens')
CONTEXT_SIZE=$(echo "$input" | jq -r '.context_window.context_window_size')

# 토큰 계산
TOTAL_TOKENS=$((INPUT_TOKENS + OUTPUT_TOKENS))
PERCENT_USED=$((TOTAL_TOKENS * 100 / CONTEXT_SIZE))

# K 단위로 변환
TOTAL_TOKENS_K=$(echo "scale=1; $TOTAL_TOKENS / 1000" | bc)
CONTEXT_SIZE_K=$(echo "scale=0; $CONTEXT_SIZE / 1000" | bc)

# 진행률 바 생성 (30자 기준)
BAR_LENGTH=30
FILLED=$((PERCENT_USED * BAR_LENGTH / 100))
if [ $FILLED -gt $BAR_LENGTH ]; then
    FILLED=$BAR_LENGTH
fi
EMPTY=$((BAR_LENGTH - FILLED))
if [ $EMPTY -lt 0 ]; then
    EMPTY=0
fi

# 사용량에 따른 색상 설정
if [ $PERCENT_USED -lt 60 ]; then
    COLOR='\033[0;32m'  # 초록색
elif [ $PERCENT_USED -lt 85 ]; then
    COLOR='\033[1;33m'  # 노란색
else
    COLOR='\033[0;31m'  # 빨간색
fi
RESET='\033[0m'

# 진행률 바 만들기
BAR="${COLOR}["
for ((i=0; i<FILLED; i++)); do BAR+="█"; done
for ((i=0; i<EMPTY; i++)); do BAR+="░"; done
BAR+="]${RESET}"

# 100% 초과 시 "압축됨" 표시
COMPRESSED_LABEL=""
if [ $PERCENT_USED -gt 100 ]; then
    COMPRESSED_LABEL=" ${COLOR}[압축됨]${RESET}"
fi

# 출력 (한글)
echo -e "[$MODEL] 컨텍스트: $BAR ${COLOR}${PERCENT_USED}%${RESET} (${TOTAL_TOKENS_K}K/${CONTEXT_SIZE_K}K)${COMPRESSED_LABEL}"