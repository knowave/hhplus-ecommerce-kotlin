#!/bin/bash

# ========================================
# 부하테스트 환경 (순수 DB) 테스트 실행 스크립트
# ========================================
#
# 사용법:
#   ./k6/run-load-test.sh [scenario]
#
# 시나리오:
#   all           - 전체 테스트 (기본값)
#   ranking       - 인기 상품 조회
#   coupon        - 쿠폰 발급
#   order-payment - 주문 및 결제
#
# 예시:
#   ./k6/run-load-test.sh              # 전체 테스트
#   ./k6/run-load-test.sh ranking      # 인기 상품 조회만
#   ./k6/run-load-test.sh coupon       # 쿠폰 발급만
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 환경 설정
PROFILE="load-test"
BASE_URL="${BASE_URL:-http://localhost:8080/api}"
SCENARIO="${1:-all}"

# 결과 저장 디렉토리
RESULTS_DIR="${SCRIPT_DIR}/results/${PROFILE}"
mkdir -p "$RESULTS_DIR"

# 타임스탬프
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo "========================================"
echo "🧪 부하테스트 환경 (순수 DB) 테스트"
echo "========================================"
echo "Profile: ${PROFILE}"
echo "Base URL: ${BASE_URL}"
echo "Scenario: ${SCENARIO}"
echo "Results: ${RESULTS_DIR}"
echo "========================================"
echo ""

# 애플리케이션이 실행 중인지 확인
check_app() {
    echo "⏳ 애플리케이션 연결 확인 중..."
    if curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/products" | grep -q "200\|404"; then
        echo "✅ 애플리케이션 연결 성공"
        return 0
    else
        echo "❌ 애플리케이션에 연결할 수 없습니다."
        echo "   다음 명령어로 애플리케이션을 실행하세요:"
        echo "   SPRING_PROFILES_ACTIVE=load-test ./gradlew bootRun"
        return 1
    fi
}

# 테스트 실행
run_test() {
    local test_name=$1
    local test_file=$2
    
    echo ""
    echo "📊 ${test_name} 테스트 시작..."
    echo "----------------------------------------"
    
    k6 run \
        --out json="${RESULTS_DIR}/${test_name}_${TIMESTAMP}.json" \
        --summary-export="${RESULTS_DIR}/${test_name}_${TIMESTAMP}_summary.json" \
        -e BASE_URL="${BASE_URL}" \
        -e PROFILE="${PROFILE}" \
        "${SCRIPT_DIR}/${test_file}"
    
    echo "✅ ${test_name} 테스트 완료"
    echo "   결과: ${RESULTS_DIR}/${test_name}_${TIMESTAMP}_summary.json"
}

# 메인 실행
check_app || exit 1

case "$SCENARIO" in
    "ranking")
        run_test "product-ranking" "scenarios/product-ranking.js"
        ;;
    "coupon")
        run_test "coupon-issue" "scenarios/coupon-issue.js"
        ;;
    "order-payment")
        run_test "order-payment" "scenarios/order-payment.js"
        ;;
    "all")
        run_test "run-all" "run-all.js"
        ;;
    *)
        echo "❌ 알 수 없는 시나리오: ${SCENARIO}"
        echo "   사용 가능한 시나리오: all, ranking, coupon, order-payment"
        exit 1
        ;;
esac

echo ""
echo "========================================"
echo "✅ 모든 테스트 완료"
echo "========================================"
echo ""
echo "결과 파일 위치: ${RESULTS_DIR}"
echo ""

