# specs/ — 规格驱动开发（SDD）产物

每个 feature 一个目录：`<YYYYMMDD>-<短横线名称>/`

```
<feature-id>/
  spec.md    # 需求 + 验收标准（sdd-spec 产出，门禁 G1 定稿）
  plan.md    # 技术方案 + 影响面 + 风险（sdd-plan 产出，门禁 G2 定稿）
  tasks.md   # 任务清单，每条带验证方式与状态（sdd-implement 维护）
```

- 流程总览见仓库外的 `FirstLanding/sdd/README.md`
- 新 feature：复制 `_TEMPLATE/` 目录
- 状态机：`draft → spec-approved → planned → implementing → ci-green → reviewed → shipped`
  状态写在 `spec.md` 顶部 frontmatter 的 `status` 字段，门禁通过后更新
