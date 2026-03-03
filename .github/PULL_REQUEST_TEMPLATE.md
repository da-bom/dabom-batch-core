## 🍀 이슈 & 티켓 넘버

<!-- 이슈 넘버와 티켓 넘버를 작성해주세요. 이슈가 닫히는 것을 원치 않으면 closed:를 지워주세요 -->

- closed: #
- jira: DABOM-xxx

---

## 🎯 목적

<!-- 왜 이 변경이 필요한지 배경과 목적을 작성해주세요. -->

## 📝 변경 사항

<!-- 무엇을 변경했는지 리스트로 작성해주세요. -->

-

## 📂 변경 범위

<!-- 변경된 레이어를 표시해주세요. 해당 항목에 [x]로 체크해주세요. -->

| Job | api (controller/service/dto) | job config | step config | reader | processor | writer | global (config/launcher/listener) |
| :-: | :--------------------------: | :--------: | :---------: | :----: | :-------: | :----: | :-------------------------------: |
|     |                              |            |             |        |           |        |                                   |

---

## 🖥️ 주요 코드 설명

<!-- 주요 코드에 대한 설명을 작성해주세요. 단순 변경이면 섹션을 지워도 됩니다. -->

```java
// 코드는 이 사이에 작성하면 됩니다.
```

## 💬 리뷰어에게

<!-- 리뷰어에게 주목했으면 하는 점 or 바라는 점을 적어주세요. -->

---

## 📋 체크리스트

<!-- PR 제출 전 확인해주세요. 해당 항목에 [x]로 체크해주세요. -->

**기본**
- [ ] Merge 대상 브랜치가 올바른가?
- [ ] `./gradlew build`가 정상적으로 통과하는가?
- [ ] Spotless / Checkstyle을 통과하는가? (`./gradlew spotlessApply checkstyleMain`)
- [ ] 전체 변경사항이 500줄을 넘지 않는가?

**배치 코드 품질**
- [ ] 의존성 방향을 준수하는가? (`Controller → Service → BatchJobLauncher → Job → Step → Reader/Processor/Writer`)
- [ ] 모든 Job에 `JobResultListener`를 등록했는가?
- [ ] Reader/Processor/Writer가 각각 단일 책임만 수행하는가?
- [ ] Job/Step 이름이 kebab-case인가?
- [ ] 하나의 Config 파일에 하나의 Job/Step만 정의했는가?

**테스트**
- [ ] 신규 배치 로직에 대한 단위 테스트를 작성했는가?

## 📌 참고 사항

<!-- 추가로 공유할 내용이 있으면 작성해주세요. (관련 문서 링크, 후속 작업 등) -->
