package com.depanalyzer.tui

import com.depanalyzer.report.VulnerabilitySeverity
import com.github.ajalt.mordant.terminal.Terminal

data class TuiDimensions(
    val width: Int,
    val height: Int,
    val leftInnerWidth: Int,
    val rightInnerWidth: Int,
    val contentRows: Int
)

private data class TuiGlyphs(
    val topLeft: String,
    val topRight: String,
    val bottomLeft: String,
    val bottomRight: String,
    val horizontal: String,
    val vertical: String,
    val topJoint: String,
    val middleLeft: String,
    val middleJoint: String,
    val middleRight: String,
    val bottomJoint: String,
    val selected: String,
    val bullet: String,
    val branch: String,
    val chain: String,
    val footerHint: String
) {
    companion object {
        val Unicode = TuiGlyphs(
            topLeft = "┌",
            topRight = "┐",
            bottomLeft = "└",
            bottomRight = "┘",
            horizontal = "─",
            vertical = "│",
            topJoint = "┬",
            middleLeft = "├",
            middleJoint = "┼",
            middleRight = "┤",
            bottomJoint = "┴",
            selected = "▶",
            bullet = "•",
            branch = "└",
            chain = "▶ ",
            footerHint = " ↑↓ navegar  PgUp/PgDn detalle  u agregar pend.  U agregar todo  a aplicar  x descartar  f filtrar  q salir "
        )

        val Ascii = TuiGlyphs(
            topLeft = "+",
            topRight = "+",
            bottomLeft = "+",
            bottomRight = "+",
            horizontal = "-",
            vertical = "|",
            topJoint = "+",
            middleLeft = "+",
            middleJoint = "+",
            middleRight = "+",
            bottomJoint = "+",
            selected = ">",
            bullet = "*",
            branch = "`",
            chain = "> ",
            footerHint = " up/down navegar  PgUp/PgDn detalle  u agregar pend.  U agregar todo  a aplicar  x descartar  f filtrar  q salir "
        )
    }
}

class TuiLayout(
    private val theme: TuiTheme = TuiTheme(),
    useUnicodeGlyphs: Boolean = true
) {
    private val glyphs = if (useUnicodeGlyphs) TuiGlyphs.Unicode else TuiGlyphs.Ascii
    private val asciiOnly = !useUnicodeGlyphs

    fun contentRows(terminal: Terminal): Int {
        val size = terminal.updateSize()
        return calculateDimensions(size.width, size.height).contentRows
    }

    fun render(terminal: Terminal, state: TuiState) {
        val size = terminal.updateSize()
        val frame = composeFrame(state, size.width, size.height)

        terminal.cursor.move {
            setPosition(1, 1)
        }
        terminal.rawPrint(frame.joinToString("\n", postfix = "\n"))
    }

    internal fun calculateDimensions(width: Int, height: Int): TuiDimensions {
        val safeWidth = if (width > 0) width else 120
        val safeHeight = if (height > 0) height else 32
        val inner = (safeWidth - 3).coerceAtLeast(28)
        val leftInner = (inner * 0.32).toInt().coerceIn(22, inner - 20)
        val rightInner = (inner - leftInner).coerceAtLeast(20)

        val fixedRows = 8
        val contentRows = (safeHeight - fixedRows).coerceAtLeast(8)

        return TuiDimensions(
            width = safeWidth,
            height = safeHeight,
            leftInnerWidth = leftInner,
            rightInnerWidth = rightInner,
            contentRows = contentRows
        )
    }

    internal fun composeFrame(state: TuiState, width: Int = 120, height: Int = 32): List<String> {
        val dim = calculateDimensions(width, height)
        if (state.loadError != null) {
            val lines = listOf(
                theme.chrome(fit(" dep-analyzer - ${state.summary.projectName} ", dim.width)),
                theme.scanDanger(fit("Error durante el escaneo: ${state.loadError}", dim.width)),
                theme.muted(fit("Presiona q para salir", dim.width))
            )
            return if (asciiOnly) lines.map(::toAscii) else lines
        }

        val safeState = if (!state.isTreeTabEnabled && state.activeTab == TuiTab.TREE) {
            state.copy(activeTab = TuiTab.DETAIL)
        } else {
            state
        }

        val normalizedState = safeState.ensureCursorBounds().ensureScrollVisible(dim.contentRows)
        val lines = mutableListOf<String>()

        lines += theme.chrome(fit(" dep-analyzer - ${state.summary.projectName} ", dim.width))
        val runtimeStatus = if (normalizedState.isLoading) {
            normalizedState.loadingMessage.ifBlank { "Escaneo en progreso..." }
        } else {
            normalizedState.statusLine
        }
        lines += theme.muted(fit(runtimeStatus, dim.width))

        val top = glyphs.topLeft +
                glyphs.horizontal.repeat(dim.leftInnerWidth) +
                glyphs.topJoint +
                glyphs.horizontal.repeat(dim.rightInnerWidth) +
                glyphs.topRight
        val leftHeader =
            "DEPENDENCIAS (${state.summary.totalEntries}) · ${state.summary.vulnerableCount} CVE · ${state.summary.outdatedCount} desact. · ${state.pendingUpdates.size} pend."
        val header = glyphs.vertical + fit(leftHeader, dim.leftInnerWidth) + glyphs.vertical +
                buildRightTabsCell(normalizedState, dim.rightInnerWidth) + glyphs.vertical
        val separator = glyphs.middleLeft +
                glyphs.horizontal.repeat(dim.leftInnerWidth) +
                glyphs.middleJoint +
                glyphs.horizontal.repeat(dim.rightInnerWidth) +
                glyphs.middleRight
        lines += top
        lines += header
        lines += separator

        val rightTitle = when (normalizedState.activeTab) {
            TuiTab.DETAIL -> "DEPENDENCIA SELECCIONADA"
            TuiTab.TREE -> {
                val selected = normalizedState.selectedEntry?.coordinate ?: "sin seleccion"
                "ARBOL DE DEPENDENCIAS - $selected"
            }
        }
        lines += glyphs.vertical + buildLeftFilterCell(normalizedState, dim.leftInnerWidth) + glyphs.vertical +
                theme.section(fit(rightTitle, dim.rightInnerWidth)) + glyphs.vertical
        lines += separator

        val bodyRows = (dim.contentRows - 2).coerceAtLeast(4)
        val leftRows = buildLeftPanelRows(normalizedState, dim.leftInnerWidth, bodyRows)
        val rightRows = buildRightPanelRows(normalizedState, dim.rightInnerWidth, bodyRows)
        for (i in 0 until bodyRows) {
            val left = leftRows.getOrElse(i) { " ".repeat(dim.leftInnerWidth) }
            val right = rightRows.getOrElse(i) { " ".repeat(dim.rightInnerWidth) }
            lines += "${glyphs.vertical}$left${glyphs.vertical}$right${glyphs.vertical}"
        }

        val bottom = glyphs.bottomLeft +
                glyphs.horizontal.repeat(dim.leftInnerWidth) +
                glyphs.bottomJoint +
                glyphs.horizontal.repeat(dim.rightInnerWidth) +
                glyphs.bottomRight
        lines += bottom
        lines += buildFooterLine(normalizedState, dim.width, bodyRows)
        return if (asciiOnly) lines.map(::toAscii) else lines
    }

    private fun buildRightTabsCell(state: TuiState, width: Int): String {
        val plain = TuiTab.entries.joinToString("  ") {
            if (it == TuiTab.TREE && !state.isTreeTabEnabled) {
                " ${it.label()} (desactivado) "
            } else {
                " ${it.label()} "
            }
        }
        if (plain.length >= width) return plain.take(width)

        val styled = TuiTab.entries.joinToString("  ") {
            val label = if (it == TuiTab.TREE && !state.isTreeTabEnabled) {
                " ${it.label()} (desactivado) "
            } else {
                " ${it.label()} "
            }

            when {
                it == TuiTab.TREE && !state.isTreeTabEnabled -> theme.tabDisabled(label)
                it == state.activeTab -> theme.tabActive(label)
                else -> theme.tabInactive(label)
            }
        }
        return styled + " ".repeat(width - plain.length)
    }

    private fun buildLeftFilterCell(state: TuiState, width: Int): String {
        val label = " Filtro (f): ${state.activeFilter.label()} "
        return theme.chipActive(fit(label, width))
    }

    private fun buildLeftPanelRows(state: TuiState, width: Int, bodyRows: Int): List<String> {
        if (state.entries.isEmpty()) {
            return listOf(fit("No hay dependencias directas para mostrar", width))
        }

        val indexes = state.filteredIndexes
        if (indexes.isEmpty()) {
            return listOf(fit("Filtro sin resultados", width))
        }

        val rows = mutableListOf<String>()
        val statusWidth = 8
        val nameWidth = (width - statusWidth - 2).coerceAtLeast(6)
        val start = state.scrollOffset.coerceIn(0, indexes.lastIndex)
        val end = (start + bodyRows).coerceAtMost(indexes.size)

        for (listIndex in start until end) {
            val entry = state.entries[indexes[listIndex]]
            val selected = listIndex == state.cursor
            val marker = if (selected) glyphs.selected else glyphs.bullet
            val name = fit(entry.coordinate, nameWidth)
            val status = theme.statusBadge(entry, statusWidth, state.pendingUpdates.containsKey(entry.coordinate))
            val plainLine = "$marker $name"
            val line = plainLine + status
            rows += if (selected) theme.selectedRow(line) else line
        }

        return rows
    }

    private fun buildRightPanelRows(state: TuiState, width: Int, bodyRows: Int): List<String> {
        val selected = state.selectedEntry ?: return listOf(fit("Selecciona una dependencia", width))
        val rows = when (state.activeTab) {
            TuiTab.DETAIL -> buildDetailRows(state, selected, width)
            TuiTab.TREE -> buildTreeRows(state, selected, width)
        }
        val maxScroll = (rows.size - bodyRows).coerceAtLeast(0)
        val start = state.detailScrollOffset.coerceIn(0, maxScroll)
        return rows.drop(start).take(bodyRows)
    }

    private fun buildDetailRows(state: TuiState, selected: TuiDependencyEntry, width: Int): List<String> {
        val rows = mutableListOf<String>()
        val versionLabel = "${selected.currentVersion} -> ${selected.latestVersion ?: selected.currentVersion}"
        rows += fit(versionLabel, width)
        val pending = state.pendingUpdates[selected.coordinate]
        when {
            pending != null -> rows += theme.scanWarn(
                fit(
                    "Pendiente: ${pending.currentVersion} -> ${pending.newVersion}",
                    width
                )
            )

            selected.updateSuggestion != null -> rows += theme.scanOk(
                fit(
                    "Sugerida: ${selected.updateSuggestion.currentVersion} -> ${selected.updateSuggestion.newVersion}",
                    width
                )
            )

            else -> rows += fit("Sin actualización sugerida", width)
        }
        rows += fit("", width)
        rows += theme.section(fit("VULNERABILIDADES ENCONTRADAS", width))

        if (selected.vulnerabilities.isEmpty()) {
            rows += theme.scanOk(fit("No se encontraron CVEs para esta dependencia", width))
        } else {
            selected.vulnerabilities.forEach { vuln ->
                rows += theme.severityBadge(vuln.severity, fit(vuln.cveId, width))
                val cvss = vuln.cvssScore?.let { "CVSS %.1f".format(it) } ?: "CVSS n/a"
                rows += fit(cvss, width)
                val description = vuln.description ?: "Sin descripcion disponible"
                rows += wrapToWidth(description, width).map { fit(it, width) }
                rows += fit("", width)
            }
        }

        rows += theme.section(fit("CADENA DE VULNERABILIDAD TRANSITIVA", width))
        val chain = if (selected.chainPreview.isEmpty()) {
            listOf("No se detecto cadena transitiva para este item")
        } else {
            selected.chainPreview
        }
        rows += chain.mapIndexed { index, node ->
            val prefix = if (index == 0) glyphs.chain else "${glyphs.branch} "
            fit(prefix + node, width)
        }

        if (!state.isTreeTabEnabled) {
            rows += fit("", width)
            rows += theme.scanWarn(fit("ARBOL TRANSITIVO DESACTIVADO", width))
            val message = state.treeUnavailableMessage ?: "No se pudo cargar el arbol transitivo"
            rows += wrapToWidth(message, width).map { fit(it, width) }
        }

        return rows
    }

    private fun buildTreeRows(state: TuiState, selected: TuiDependencyEntry, width: Int): List<String> {
        if (!state.isTreeTabEnabled) {
            val message = state.treeUnavailableMessage ?: "No se pudo cargar el arbol transitivo"
            return listOf(theme.scanWarn(fit("ARBOL TRANSITIVO DESACTIVADO", width))) +
                    wrapToWidth(message, width).map { fit(it, width) }
        }

        if (selected.transitiveTreeLines.isEmpty()) {
            return listOf(fit("No se encontró árbol transitivo para esta dependencia", width))
        }

        return selected.transitiveTreeLines.map { line ->
            val fitted = fit(line, width)
            when {
                line.contains("[CHAIN]", ignoreCase = true) -> theme.scanDanger(fitted)
                line.trimStart().startsWith("! ") -> theme.scanDanger(fitted)
                line.contains("CVE", ignoreCase = true) -> theme.scanDanger(fitted)
                line.contains("desactualizada", ignoreCase = true) -> theme.scanWarn(fitted)
                else -> fitted
            }
        }
    }

    private fun buildFooterLine(state: TuiState, width: Int, viewportRows: Int): String {
        val visible = if (state.filteredIndexes.isEmpty()) {
            "0"
        } else {
            val first = state.scrollOffset + 1
            val last = (state.scrollOffset + viewportRows).coerceAtMost(state.filteredIndexes.size)
            "$first-$last"
        }

        val hint = state.confirmationPrompt
            ?: glyphs.footerHint
        val summary = "[$visible / ${state.filteredIndexes.size}]"
        val base = fit(hint, (width - summary.length).coerceAtLeast(0)) + summary
        return if (state.confirmationPrompt != null) theme.scanWarn(base) else theme.muted(base)
    }

    private fun wrapToWidth(value: String, width: Int): List<String> {
        if (value.isBlank()) return listOf("")
        if (value.length <= width) return listOf(value)
        val words = value.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            if (current.isEmpty()) {
                current = word
            } else if (current.length + word.length + 1 <= width) {
                current = "$current $word"
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun fit(text: String, width: Int): String {
        val safe = text.replace("\n", " ")
        return if (safe.length >= width) safe.take(width) else safe.padEnd(width)
    }

    private fun toAscii(text: String): String {
        return text
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ñ", "N")
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace("·", "|")
            .replace("→", "->")
            .replace("←", "<-")
            .replace("↑", "up")
            .replace("↓", "down")
            .map { char -> if (char.code in 32..126 || char == '\u001B') char else '?' }
            .joinToString("")
    }
}

internal fun VulnerabilitySeverity.priority(): Int = when (this) {
    VulnerabilitySeverity.CRITICAL -> 5
    VulnerabilitySeverity.HIGH -> 4
    VulnerabilitySeverity.MEDIUM -> 3
    VulnerabilitySeverity.LOW -> 2
    VulnerabilitySeverity.UNKNOWN -> 1
}
