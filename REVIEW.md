# REVIEW.md: Maven Dependency Tracker コードレビュー

- PomParser.kt line 100-104
  - Given: project.dependencyManagement.dependencies.dependencyの中に居る時（inDependencies=true, inDependencyManagement=true を想定）
  - Expect: dependenciesではなく、dependencyManagementに要素が追加されてほしい
  - What: dependencyManagementではなく、dependenciesに要素が追加されてしまうのではないか