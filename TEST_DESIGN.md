# 🧪 MentoAI Backend 테스트 설계 문서

## 1. 테스트 전략

### 1.1 테스트 목표
- **품질 보증**: 각 기능이 요구사항대로 동작하는지 검증
- **회귀 방지**: 새로운 코드가 기존 기능을 망가뜨리지 않는지 확인
- **문서화**: 테스트 코드 자체가 기능 명세서 역할

### 1.2 테스트 레벨
```
┌─────────────────────────────────────┐
│   E2E 테스트 (통합 테스트)          │ ← API 전체 흐름 테스트
├─────────────────────────────────────┤
│   통합 테스트                        │ ← Service + Repository
├─────────────────────────────────────┤
│   단위 테스트 ⭐                     │ ← Service, Controller 개별 테스트
└─────────────────────────────────────┘
```

### 1.3 테스트 범위
- **Service Layer**: 비즈니스 로직 테스트 (핵심)
- **Controller Layer**: API 엔드포인트 테스트
- **Repository Layer**: 데이터베이스 쿼리 테스트

---

## 2. 테스트 설계 원칙

### 2.1 AAA 패턴 (Arrange-Act-Assert)
```java
@Test
void createActivity_Success() {
    // Arrange (준비): 테스트 데이터 설정
    ActivityEntity activity = new ActivityEntity();
    activity.setTitle("테스트 활동");
    
    // Act (실행): 테스트 대상 메서드 실행
    ActivityEntity result = activityService.createActivity(activity);
    
    // Assert (검증): 결과 확인
    assertEquals("테스트 활동", result.getTitle());
}
```

### 2.2 Given-When-Then 패턴
```java
@Test
void getUser_Success() {
    // Given: 주어진 상황
    when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
    
    // When: 이런 동작을 하면
    Optional<UserEntity> result = userService.getUser(1L);
    
    // Then: 이런 결과가 나온다
    assertTrue(result.isPresent());
}
```

### 2.3 테스트 명명 규칙
```
메서드명_테스트상황_예상결과

예시:
- createUser_Success → 사용자 생성 성공 케이스
- createUser_DuplicateEmail → 이메일 중복 실패 케이스
- getActivity_NotFound → 활동 조회 실패 케이스
```

---

## 3. 테스트 커버리지 계획

### 3.1 Service Layer 테스트

#### ActivityService (우선순위: 높음)
| 메서드 | 테스트 케이스 | 상태 |
|--------|--------------|------|
| createActivity | ✅ 정상 생성 | 완료 |
| createActivity | ⚠️ 필수값 누락 | 예정 |
| getActivity | ✅ ID로 조회 성공 | 완료 |
| getActivity | ✅ 존재하지 않는 ID | 완료 |
| updateActivity | ✅ 수정 성공 | 완료 |
| updateActivity | ⚠️ 존재하지 않는 ID | 예정 |
| deleteActivity | ✅ 삭제 성공 | 완료 |
| deleteActivity | ✅ 존재하지 않는 ID | 완료 |
| getActivities | ✅ 필터링 조회 | 완료 |

**현재 커버리지**: 8/10 (80%)

#### UserService (우선순위: 높음)
| 메서드 | 테스트 케이스 | 상태 |
|--------|--------------|------|
| createUser | ✅ 정상 생성 | 완료 |
| createUser | ✅ 이메일 중복 | 완료 |
| getUser | ✅ 조회 성공 | 완료 |
| getUser | ✅ 조회 실패 | 완료 |
| updateUser | ✅ 수정 성공 | 완료 |
| deleteUser | ✅ 삭제 성공 | 완료 |
| deleteUser | ✅ 삭제 실패 | 완료 |

**현재 커버리지**: 7/7 (100%)

#### RecommendService (우선순위: 중간)
| 메서드 | 테스트 케이스 | 상태 |
|--------|--------------|------|
| getRecommendations | ✅ 맞춤 추천 성공 | 완료 |
| getRecommendations | ✅ 사용자 없음 | 완료 |
| semanticSearch | ✅ 검색 성공 | 완료 |
| semanticSearch | ✅ 빈 검색어 | 완료 |
| getTrendingActivities | ✅ 인기 활동 조회 | 완료 |
| getSimilarActivities | ✅ 유사 활동 추천 | 완료 |
| getSimilarActivities | ✅ 활동 없음 | 완료 |

**현재 커버리지**: 7/7 (100%)

---

## 4. 테스트 케이스 상세

### 4.1 ActivityService 테스트

#### 테스트 1: 활동 생성 성공
```java
목적: 정상적인 활동 생성이 되는지 확인
입력: 
  - title: "테스트 활동"
  - content: "테스트 내용"
  - type: STUDY
예상 결과:
  - 활동 생성 성공
  - 반환된 활동의 title이 "테스트 활동"
  - Repository의 save() 메서드가 1번 호출됨
```

#### 테스트 2: 활동 조회 실패
```java
목적: 존재하지 않는 ID로 조회 시 빈 Optional 반환
입력: 
  - id: 999 (존재하지 않는 ID)
예상 결과:
  - Optional.empty() 반환
  - Repository의 findById()가 1번 호출됨
```

### 4.2 UserService 테스트

#### 테스트 1: 이메일 중복 체크
```java
목적: 중복된 이메일로 가입 시 예외 발생
입력:
  - email: "test@example.com" (이미 존재하는 이메일)
예상 결과:
  - IllegalArgumentException 발생
  - Repository의 save()가 호출되지 않음
```

### 4.3 RecommendService 테스트

#### 테스트 1: 의미 기반 검색
```java
목적: 검색어를 기반으로 관련 활동 추천
입력:
  - query: "개발"
  - limit: 5
예상 결과:
  - 관련 활동 목록 반환
  - 동의어 확장 적용 ("프로그래밍", "코딩", "소프트웨어")
```

---

## 5. 모킹(Mocking) 전략

### 5.1 왜 모킹을 사용하나?
```
실제 데이터베이스 ❌ → 느리고, 의존성 높음
모킹 객체 ✅ → 빠르고, 독립적인 테스트
```

### 5.2 모킹 대상
```java
@Mock
private ActivityRepository activityRepository;  // DB 접근 모킹

@Mock
private NotificationService notificationService;  // 외부 서비스 모킹

@InjectMocks
private ActivityService activityService;  // 테스트 대상
```

### 5.3 모킹 예시
```java
// Repository가 특정 값을 반환하도록 설정
when(activityRepository.findById(1L))
    .thenReturn(Optional.of(testActivity));

// 메서드가 호출되었는지 검증
verify(activityRepository, times(1)).save(any());
```

---

## 6. 테스트 실행 및 결과

### 6.1 테스트 실행 명령어
```bash
# 전체 테스트 실행
./gradlew test

# 특정 클래스만 테스트
./gradlew test --tests ActivityServiceTest

# 테스트 + 커버리지 리포트
./gradlew test jacocoTestReport
```

### 6.2 예상 테스트 결과
```
ActivityServiceTest
  ✅ createActivity_Success
  ✅ getActivity_Success
  ✅ getActivity_NotFound
  ✅ updateActivity_Success
  ✅ deleteActivity_Success
  ✅ deleteActivity_NotFound
  ✅ getActiveActivities_Success
  ✅ getCampusActivities_Success

UserServiceTest
  ✅ createUser_Success
  ✅ createUser_DuplicateEmail
  ✅ getUser_Success
  ✅ getUser_NotFound
  ✅ updateUser_Success
  ✅ deleteUser_Success
  ✅ deleteUser_NotFound

RecommendServiceTest
  ✅ getRecommendations_Success
  ✅ getRecommendations_UserNotFound
  ✅ semanticSearch_Success
  ✅ semanticSearch_EmptyQuery
  ✅ getTrendingActivities_Success
  ✅ getSimilarActivities_Success
  ✅ getSimilarActivities_NotFound

총 22개 테스트 - 모두 통과 ✅
실행 시간: 약 2초
```

---

## 7. 테스트 개선 계획

### 7.1 단기 목표 (1주)
- [ ] Controller 테스트 추가 (MockMvc)
- [ ] 예외 케이스 테스트 확대
- [ ] 테스트 커버리지 90% 달성

### 7.2 중기 목표 (1개월)
- [ ] 통합 테스트 추가 (@SpringBootTest)
- [ ] 성능 테스트 추가
- [ ] CI/CD 파이프라인 연동

### 7.3 장기 목표 (3개월)
- [ ] E2E 테스트 자동화
- [ ] 부하 테스트 (JMeter)
- [ ] 보안 테스트

---

## 8. 발표 포인트

### 8.1 강조할 점
1. **체계적인 테스트 설계**
   - AAA 패턴 적용
   - 명확한 테스트 케이스 분류

2. **높은 커버리지**
   - Service Layer 80% 이상
   - 핵심 기능 100% 커버

3. **모킹을 활용한 독립성**
   - DB 의존성 제거
   - 빠른 테스트 실행

### 8.2 시연 순서
1. 테스트 코드 구조 설명
2. 실제 테스트 실행 (./gradlew test)
3. 테스트 결과 확인
4. 커버리지 리포트 확인

---

## 9. 테스트 코드의 가치

### 9.1 개발자 관점
- **자신감**: 코드 변경 시 기존 기능이 깨지지 않았음을 보장
- **문서화**: 코드 사용 방법을 테스트로 설명
- **설계 개선**: 테스트하기 쉬운 코드 = 좋은 설계

### 9.2 프로젝트 관점
- **품질 향상**: 버그를 조기에 발견
- **유지보수**: 리팩토링 시 안정성 확보
- **협업**: 다른 개발자가 코드를 이해하기 쉬움

---

## 10. 참고 자료

### 테스트 프레임워크
- **JUnit 5**: Java 단위 테스트 프레임워크
- **Mockito**: 모킹 라이브러리
- **AssertJ**: 가독성 좋은 assertion

### 모범 사례
- 테스트는 독립적이어야 함 (다른 테스트에 영향 X)
- 테스트는 반복 가능해야 함 (실행할 때마다 같은 결과)
- 테스트는 빨라야 함 (피드백 사이클 단축)

---

**작성일**: 2024년 10월 18일  
**작성자**: MentoAI Backend Team  
**버전**: 1.0















