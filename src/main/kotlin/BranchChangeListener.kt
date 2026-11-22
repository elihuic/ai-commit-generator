package com.hiper.iacommit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.util.concurrent.ConcurrentHashMap

class BranchChangeListener(private val project: Project) : GitRepositoryChangeListener {

    private val lastKnownBranches = ConcurrentHashMap<String, String>()

    override fun repositoryChanged(repository: GitRepository) {
        val repoPath = repository.root.path
        val currentBranch = repository.currentBranchName ?: return
        val previousBranch = lastKnownBranches.put(repoPath, currentBranch)

        if (previousBranch != null && previousBranch != currentBranch) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                clearCommitMessage()
            }
        }
    }

    private fun clearCommitMessage() {
        // 1. Limpia el historial del último mensaje guardado en VCS
        VcsConfiguration.getInstance(project).LAST_COMMIT_MESSAGE = ""

        // 2. Limpia el comentario de la changelist por defecto mediante editComment
        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.editComment(changeListManager.defaultChangeList.name, "")

        // 3. Busca el componente visual CommitMessage dentro de la ventana Commit y lo vacía
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Commit")
        val contentComponent = toolWindow?.contentManager?.selectedContent?.component
        if (contentComponent != null) {
            val commitMessageComponent = UIUtil.findComponentOfType(contentComponent, CommitMessage::class.java)
            commitMessageComponent?.setCommitMessage("")
        }
    }
}