package com.hiper.iacommit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.Refreshable
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GenerateCommitAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repoManager = GitRepositoryManager.getInstance(project)
        val currentRepo = repoManager.repositories.firstOrNull() ?: return
        val branchName = currentRepo.currentBranch?.name ?: "main"

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generando commit...", false) {
            override fun run(indicator: ProgressIndicator) {
                val settings = CommitGeneratorSettings.getInstance()
                val ticket = extractTicket(branchName)
                val codeDiff = getGitDiff(currentRepo)
                val changedFiles = getChangedFiles(currentRepo)

                val description = if (settings.apiKey.isNotBlank()) {
                    generateAiDescription(settings, codeDiff, changedFiles, branchName)
                } else {
                    compactOfflineDescription(branchName, changedFiles)
                }

                val finalMessage = if (ticket.isNotEmpty()) "$ticket: $description" else description

                ApplicationManager.getApplication().invokeLater {
                    val commitControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
                    if (commitControl != null) {
                        commitControl.setCommitMessage(finalMessage)
                    } else {
                        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel
                        panel?.commitMessage = finalMessage
                    }
                }
            }
        })
    }

    private fun extractTicket(branchName: String): String {
        val withoutPrefix = branchName.substringAfterLast("/")
        val ticketRegex = """^([A-Za-z0-9]+-\d+)""".toRegex()
        return ticketRegex.find(withoutPrefix)?.groupValues?.get(1)?.uppercase() ?: ""
    }

    private fun getGitDiff(repo: GitRepository): String {
        val stagedHandler = GitLineHandler(repo.project, repo.root, GitCommand.DIFF)
        stagedHandler.addParameters("--cached")
        val stagedResult = Git.getInstance().runCommand(stagedHandler)
        if (stagedResult.outputAsJoinedString.isNotBlank()) {
            return stagedResult.outputAsJoinedString.take(2500)
        }

        val unstagedHandler = GitLineHandler(repo.project, repo.root, GitCommand.DIFF)
        val unstagedResult = Git.getInstance().runCommand(unstagedHandler)
        return unstagedResult.outputAsJoinedString.take(2500)
    }

    private fun getChangedFiles(repo: GitRepository): List<String> {
        val handler = GitLineHandler(repo.project, repo.root, GitCommand.STATUS)
        handler.addParameters("--porcelain")
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) return emptyList()

        return result.output.mapNotNull { line ->
            val path = line.drop(3).trim()
            if (path.isNotEmpty()) path.substringAfterLast("/") else null
        }.distinct()
    }

    private fun generateAiDescription(
        settings: CommitGeneratorSettings,
        diff: String,
        changedFiles: List<String>,
        branchName: String
    ): String {
        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()

            val contextContent = if (diff.isNotBlank()) diff else "Archivos: ${changedFiles.joinToString(", ")}"

            val systemPrompt = "Resume el cambio en español en MÁXIMO 6 palabras, en minúsculas, sin punto final ni comillas. Ejemplo: agrega filtro de logueo http"
            val userPrompt = "Rama: $branchName\nDiff:\n$contextContent"

            val jsonBody = """
                {
                  "model": "${escapeJson(settings.modelName)}",
                  "messages": [
                    {"role": "system", "content": "${escapeJson(systemPrompt)}"},
                    {"role": "user", "content": "${escapeJson(userPrompt)}"}
                  ],
                  "temperature": 0.1,
                  "max_tokens": 25
                }
            """.trimIndent()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(settings.endpointUrl.trim()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${settings.apiKey.trim()}")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val contentRegex = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
                val rawContent = contentRegex.find(response.body())?.groupValues?.get(1)
                val cleaned = rawContent
                    ?.replace("\\n", " ")
                    ?.replace("\\\"", "\"")
                    ?.trim()
                    ?.removeSuffix(".")

                if (!cleaned.isNullOrBlank()) return cleaned
            }
            compactOfflineDescription(branchName, changedFiles)
        } catch (e: Exception) {
            compactOfflineDescription(branchName, changedFiles)
        }
    }

    private fun compactOfflineDescription(branchName: String, changedFiles: List<String>): String {
        val withoutPrefix = branchName.substringAfterLast("/")
        val branchDesc = withoutPrefix.replace(TICKET_STRIP_REGEX, "")
            .replace("-", " ")
            .replace("_", " ")
            .trim()

        if (branchDesc.isNotEmpty()) return branchDesc
        if (changedFiles.isNotEmpty()) return "actualiza ${changedFiles.first()}"
        return "actualización de código"
    }

    private fun escapeJson(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

    companion object {
        private val TICKET_STRIP_REGEX = "^[A-Za-z0-9]+-\\d+[-_]?\\s*".toRegex(RegexOption.IGNORE_CASE)
    }
}