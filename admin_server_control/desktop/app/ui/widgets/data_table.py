from datetime import datetime

from PySide6.QtCore import QEvent, QObject, QRectF, QSize, Qt, QThreadPool, QTimer, Signal
from PySide6.QtGui import QColor, QFontMetrics, QIcon, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QAbstractItemView,
    QApplication,
    QComboBox,
    QFrame,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QPushButton,
    QStyle,
    QStyledItemDelegate,
    QStyleOptionViewItem,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from app.core import http, server_address
from app.core.worker import Worker
from app.ui.widgets.busy import set_spinning
from app.ui import theme
from app.ui.icons import icon

# "in the products are in desktop add a place to see the images of the
# individual products row for easy identification even there" - opt-in,
# not a change to every DataTable-using section: only fires when a row
# actually has a column literally named one of these (Products has
# "image", Campaigns has "media"), and only for rows where that value
# looks like a URL - everything else renders exactly as before.
THUMBNAIL_COLUMN_KEYS = ("image", "media")
VIDEO_SUFFIXES = (".mp4", ".mov", ".webm", ".3gp", ".m4v")
THUMBNAIL_SIZE = 40
# Space inside the table card - left more than right, "keep a space from the
# left edge" - and the room kept for the up-down scroll bar.
CARD_MARGIN_LEFT = 18
CARD_MARGIN_RIGHT = 10
SCROLLBAR_ROOM = 14
QWIDGETSIZE_MAX = 16777215
# A column is "long text" when any value is longer than this; it is then at
# most LONG_COLUMN_WIDTH wide, wraps, and shows the first LONG_TEXT_PREVIEW
# characters (about three lines) - the rest is in the tooltip.
LONG_TEXT_CHARS = 60
LONG_COLUMN_WIDTH = 300
LONG_TEXT_PREVIEW = 140

# "specifying all, only 50 etc" - the same limit choice everywhere a list
# of rows is shown, not just the database browser.
LIMIT_OPTIONS = [("50", 50), ("100", 100), ("200", 200), ("500", 500), ("All", None)]

# "the background color is not so good provide a better color ... or an
# alternative style" - a flat light-pink background looked out of place
# against this app's dark forest theme. Same technique the stylesheet
# already uses for a selected row (a translucent tint of a theme color
# over the dark surface, theme.py's `rgba(235, 154, 75, 0.32)`) - here a
# low-alpha wash of theme.DANGER, plus strikethrough + danger-colored text
# so a deleted row still reads clearly without relying on color alone.
DELETED_FLAG_KEYS = ("Is_Deleted", "isDeleted")
DELETED_ROW_BACKGROUND = QColor(theme.DANGER)
DELETED_ROW_BACKGROUND.setAlpha(40)
DELETED_ROW_FOREGROUND = QColor(theme.DANGER)

# Product and campaign pictures already fetched, by address - a re-filter
# (each letter typed into a search box) or a refresh shows them at once
# instead of downloading them all again.
_THUMBNAILS: dict[str, QIcon] = {}


def _is_deleted_row(row: dict) -> bool:
    for key in DELETED_FLAG_KEYS:
        if key in row:
            value = row[key]
            return value in (True, 1, "1")
    return False


# "you can show different colors in a certain column based on its
# importance" - a status-like column shows its value as a small rounded
# pill coloured by meaning, so a FAILED or a PENDING stands out at a glance.
# Columns are recognised by name (spaces, underscores and case ignored).
STATUS_COLUMNS = {
    "status", "payment", "paymentstatus", "orderstatus", "deliverystatus", "state",
    "role", "rolename", "active", "isactive", "mediatype", "kind",
}
_GOOD = ("COMPLETED", "PAID", "DELIVERED", "ACTIVE", "APPROVED", "CONFIRMED", "SUCCESS", "TRUE", "YES", "LIVE", "OPEN", "SENT")
_WAIT = ("PENDING", "NEW", "PROCESSING", "IN_PROGRESS", "IN PROGRESS", "DRAFT", "PAUSED", "WAITING", "PARTIAL", "UNASSIGNED", "QUEUED")
_BAD = ("FAILED", "CANCELLED", "CANCELED", "REJECTED", "BLOCKED", "REVOKED", "DISABLED", "FALSE", "NO", "EXPIRED", "OVERDUE", "DROPPED", "DELETED")
# Money columns are right-aligned with thousands separators.
MONEY_WORDS = ("amount", "total", "price", "kes", "cost", "balance", "refunded", "paid")


def _header_label(name: str) -> str:
    """A field name as a column title: "productId" -> "Product ID",
    "marketer_note" -> "Marketer note". Titles a section already wrote
    for people (with spaces, or "M-Pesa receipt") are kept as they are."""
    if " " in name or not name:
        return name
    import re
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name.replace("_", " ")).split()
    words = ["ID" if w.lower() == "id" else w.lower() for w in words]
    text = " ".join(words)
    return text[:1].upper() + text[1:]


def _column_key(name: str) -> str:
    return "".join(ch for ch in name.lower() if ch.isalnum())


def _is_status_column(name: str) -> bool:
    return _column_key(name) in STATUS_COLUMNS


def _is_money_column(name: str) -> bool:
    key = _column_key(name)
    return any(word in name.lower() for word in MONEY_WORDS) and not key.endswith("id") and not key.endswith("at")


def _number(value):
    """The value as a number, or None when it isn't one."""
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return value
    try:
        return float(str(value).replace(",", ""))
    except ValueError:
        return None


def _status_text(value) -> str:
    """How a status value reads in its pill: true/false as Active/Disabled."""
    if isinstance(value, bool):
        return "Active" if value else "Disabled"
    return "" if value is None else str(value)


def _status_color(text: str) -> str | None:
    word = text.strip().upper()
    if not word:
        return None
    for group, color in ((_BAD, theme.STATUS_BAD), (_WAIT, theme.STATUS_WAIT), (_GOOD, theme.STATUS_GOOD)):
        if any(word == w or word.startswith(w + " ") or word.startswith(w + "-") for w in group):
            return color
    return theme.STATUS_INFO


def _preview(text: str) -> str:
    """The start of a long text for its cell: whole words up to
    LONG_TEXT_PREVIEW characters, then "..." (line breaks become spaces)."""
    flat = " ".join(text.split())
    if len(flat) <= LONG_TEXT_PREVIEW:
        return flat
    cut = flat[:LONG_TEXT_PREVIEW]
    if " " in cut:
        cut = cut.rsplit(" ", 1)[0]
    return cut.rstrip(" ,.;:-") + " ..."


def _money_text(number) -> str:
    return f"{number:,.0f}" if float(number).is_integer() else f"{number:,.2f}"


class StatusPillDelegate(QStyledItemDelegate):
    """Paints a status cell as a rounded pill: a soft wash of its colour, a
    thin outline and the text in that colour."""

    def paint(self, painter: QPainter, option: QStyleOptionViewItem, index):
        text = index.data(Qt.ItemDataRole.DisplayRole) or ""
        color = _status_color(text)
        opt = QStyleOptionViewItem(option)
        self.initStyleOption(opt, index)
        opt.text = ""
        widget = opt.widget
        style = widget.style() if widget is not None else QApplication.style()
        style.drawControl(QStyle.ControlElement.CE_ItemViewItem, opt, painter, widget)
        if not text or color is None:
            return
        painter.save()
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        metrics = QFontMetrics(opt.font)
        width = min(metrics.horizontalAdvance(text) + 22, opt.rect.width() - 12)
        height = min(metrics.height() + 8, opt.rect.height() - 6)
        alignment = index.data(Qt.ItemDataRole.TextAlignmentRole)
        centered = alignment is not None and bool(int(alignment) & int(Qt.AlignmentFlag.AlignHCenter))
        left = opt.rect.x() + ((opt.rect.width() - width) // 2 if centered else 10)
        pill = QRectF(left, opt.rect.y() + (opt.rect.height() - height) / 2, width, height)
        fill = QColor(color)
        fill.setAlpha(34 if theme.IS_LIGHT else 46)
        edge = QColor(color)
        edge.setAlpha(150)
        painter.setPen(QPen(edge, 1))
        painter.setBrush(fill)
        painter.drawRoundedRect(pill, height / 2, height / 2)
        # Pale text on the dark themes; on Light the same colour darkened, or
        # the words all but disappear on white.
        painter.setPen(QColor(color).darker(165) if theme.IS_LIGHT else QColor(color).lighter(125))
        painter.drawText(pill, int(Qt.AlignmentFlag.AlignCenter), metrics.elidedText(text, Qt.TextElideMode.ElideRight, int(width) - 12))
        painter.restore()

    def sizeHint(self, option, index):
        hint = super().sizeHint(option, index)
        return QSize(hint.width() + 24, hint.height())


class CellNote(str):
    """A cell shown with a line icon (and, optionally, its own colour) in
    front of its text - "Not set" with a warning sign, say. Still a plain
    string to everything else."""

    def __new__(cls, text: str, icon_name: str, color: str | None = None):
        note = super().__new__(cls, text)
        note.icon = icon_name
        note.color = color
        return note


class DataTable(QWidget):
    """One generic, reusable table-with-toolbar - every section (Products,
    Marketing, Trips, Users, the raw database browser, ...) is this same
    widget pointed at a different endpoint, rather than a bespoke table
    per section. Columns are derived from whatever keys the rows actually
    have, so it adapts to any JSON shape without per-section wiring."""

    refresh_requested = Signal(object)  # emits the chosen limit (int or None for "All")
    row_selected = Signal(dict)
    # Plain single click - for panels where highlighting a row should already
    # switch which actions apply (row_selected stays double-click only, since
    # some callers open a dialog from it).
    row_clicked = Signal(dict)

    def __init__(
        self,
        title: str,
        show_limit_selector: bool = True,
        default_limit_label: str = "50",
        parent=None,
        searchable: bool = False,
        search_placeholder: str = "Search",
    ):
        super().__init__(parent)
        # "if the row is not long enough to be scrolled horizontally then try
        # as much to center the rows in the middle of the big card ... but if
        # its long then its ok" - every table: when its columns fit, they
        # share the whole width and the cells are centred; when they don't,
        # each column keeps its own width (text left, money right) and the
        # table scrolls sideways. Decided again whenever the table is resized.
        self._content_widths: list[int] = []
        self._fitted: bool | None = None
        # _source_rows: everything the section gave; _rows: what's on show
        # (the same, unless a search narrows it) - selection uses _rows.
        self._source_rows: list[dict] = []
        self._rows: list[dict] = []
        self._render_args = (None, None, None)  # total, row_color, empty_cells
        self._loaded_at = datetime.now()
        self._refresh_pending = False
        # Keys that stay in each row's data (for selection callbacks) but
        # aren't shown as a column - e.g. a raw database id when a tidy
        # running number is shown instead.
        self.hidden_columns: set[str] = set()
        # Bumped on every set_rows() call - an in-flight thumbnail fetch
        # checks its own generation before applying, so a slow download
        # that finishes after a refresh/re-sort never paints a pixmap
        # onto what is now a different row.
        self._generation = 0
        # See GenericListSection - keeps in-flight Workers from being
        # garbage-collected before their signals fire.
        self._active_workers = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        toolbar = QHBoxLayout()
        title_label = QLabel(title)
        title_label.setProperty("role", "title")
        toolbar.addWidget(title_label)

        # "check areas like product, marketing ... to check by name ... for
        # faster identification" - an opt-in search box that narrows the
        # rows as you type (any visible column; phone numbers however
        # they're written).
        self.search_box = None
        if searchable:
            self.search_box = QLineEdit()
            self.search_box.setPlaceholderText(search_placeholder)
            self.search_box.setClearButtonEnabled(True)
            self.search_box.setMinimumWidth(240)
            self.search_box.setMaximumWidth(360)
            self.search_box.textChanged.connect(self._on_search_changed)
            toolbar.addSpacing(14)
            toolbar.addWidget(self.search_box)
        toolbar.addStretch()

        self.limit_combo = None
        if show_limit_selector:
            toolbar.addWidget(QLabel("Show:"))
            self.limit_combo = QComboBox()
            for label, _value in LIMIT_OPTIONS:
                self.limit_combo.addItem(label)
            # "i should see all the trips nothing hidden not unless i
            # select the 50 only etc" - most sections default to a capped
            # 50 (large tables), but a section can ask for "All" as its
            # own starting point instead.
            self.limit_combo.setCurrentText(default_limit_label)
            self.limit_combo.currentIndexChanged.connect(lambda _i: self.refresh_requested.emit(self.current_limit()))
            toolbar.addWidget(self.limit_combo)

        self.refresh_btn = QPushButton("Refresh")
        self.refresh_btn.setIcon(icon("refresh"))
        self.refresh_btn.clicked.connect(self._on_refresh_clicked)
        toolbar.addWidget(self.refresh_btn)

        self.count_label = QLabel("")
        self.count_label.setProperty("role", "muted")
        toolbar.addWidget(self.count_label)

        layout.addLayout(toolbar)

        self.table = QTableWidget()
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setAlternatingRowColors(True)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.table.verticalHeader().setVisible(False)
        # The modern look (theme.py): no grid, roomy rows, smooth scrolling.
        self.table.setShowGrid(False)
        # On for the long-text columns (capped width, a few lines); the
        # others are sized to their contents, so they never wrap.
        self.table.setWordWrap(True)
        self.table.verticalHeader().setDefaultSectionSize(38)
        self.table.horizontalHeader().setHighlightSections(False)
        # When a long table scrolls, the last column still takes any spare
        # width, so each row (and its stripe) runs edge to edge.
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.horizontalHeader().setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.table.setHorizontalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.table.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self._pill_delegate = StatusPillDelegate(self.table)
        self._plain_delegate = QStyledItemDelegate(self.table)
        self.table.cellDoubleClicked.connect(self._on_cell_double_clicked)
        self.table.cellClicked.connect(self._on_cell_clicked)

        # The rounded card fills the space; the table inside is only as wide
        # as its columns and sits in the middle (equal stretch either side).
        # A table too wide for the card fills it and scrolls sideways.
        self.card = QFrame()
        self.card.setObjectName("tableCard")
        self._card_layout = QHBoxLayout(self.card)
        # "keep a space from the left edge it feels so close"
        self._card_layout.setContentsMargins(CARD_MARGIN_LEFT, 8, CARD_MARGIN_RIGHT, 8)
        self._card_layout.setSpacing(0)
        self._card_layout.addStretch(1)
        self._card_layout.addWidget(self.table)
        self._card_layout.addStretch(1)
        self.card.installEventFilter(self)
        layout.addWidget(self.card, 1)

    def eventFilter(self, watched: QObject, event: QEvent) -> bool:
        if watched is self.card and event.type() == QEvent.Type.Resize:
            self._fit_columns()
        return super().eventFilter(watched, event)

    def _fit_columns(self):
        """"if the row is not long enough to be scrolled horizontally ...
        center the rows in the middle of the big card ... but if its long
        then its ok" - columns that fit keep their own widths, as a block
        in the middle of the card; columns that don't fill the card and
        scroll. Uses the widths measured when the rows were drawn, so a
        resize is only a comparison."""
        count = self.table.columnCount()
        layout = self._card_layout
        if not count or len(self._content_widths) != count:
            # Nothing to lay out (no rows, an error): the table fills the card.
            self.table.setMinimumWidth(0)
            self.table.setMaximumWidth(QWIDGETSIZE_MAX)
            layout.setStretch(0, 0)
            layout.setStretch(1, 1)
            layout.setStretch(2, 0)
            return
        # Room for the up-down scroll bar too, so it never covers a column.
        needed = sum(self._content_widths) + 2 * self.table.frameWidth() + SCROLLBAR_ROOM
        available = self.card.contentsRect().width() - CARD_MARGIN_LEFT - CARD_MARGIN_RIGHT
        fits = needed <= available
        if fits:
            self.table.setFixedWidth(needed)
            layout.setStretch(0, 1)
            layout.setStretch(1, 0)
            layout.setStretch(2, 1)
        else:
            self.table.setMinimumWidth(0)
            self.table.setMaximumWidth(QWIDGETSIZE_MAX)
            layout.setStretch(0, 0)
            layout.setStretch(1, 1)
            layout.setStretch(2, 0)
        if fits != self._fitted:
            # Just switched (or a fresh draw): back to the measured widths,
            # which the person may then drag wider or narrower.
            header = self.table.horizontalHeader()
            for c, width in enumerate(self._content_widths):
                header.resizeSection(c, width)
            header.setStretchLastSection(False)
            header.setStretchLastSection(True)
            self._fitted = fits

    def _set_table_state(self, name: str, on: bool):
        # The table dims while loading; the card's border flashes when done.
        for widget in (self.table, self.card):
            widget.setProperty(name, on)
            widget.style().unpolish(widget)
            widget.style().polish(widget)

    def _stamp(self, text: str) -> str:
        # "Updated 14:32:05" - proof, on every reload, that the data really
        # was fetched again even when nothing on screen happened to change.
        # (The time of the fetch, not of the last letter typed in a search.)
        return f"{text}  \u00b7  updated {self._loaded_at:%H:%M:%S}"

    def _finish_refresh(self, ok: bool):
        """Called when rows (or an error) arrive: stop the busy/dim state,
        flash the table border once, and - if this followed the Refresh
        button - say so on the button for a moment."""
        set_spinning(self.refresh_btn, False)
        self._set_table_state("loading", False)
        self._set_table_state("flash", True)
        flash = QTimer(self.table)
        flash.setSingleShot(True)
        flash.timeout.connect(lambda: self._set_table_state("flash", False))
        flash.start(700)
        if self._refresh_pending:
            self._refresh_pending = False
            self.refresh_btn.setText("Updated" if ok else "Failed")
            self.refresh_btn.setIcon(icon("check") if ok else icon("warning", theme.DANGER))
            reset = QTimer(self.refresh_btn)
            reset.setSingleShot(True)
            reset.timeout.connect(lambda: (self.refresh_btn.setText("Refresh"), self.refresh_btn.setIcon(icon("refresh"))))
            reset.start(1600)

    def _on_refresh_clicked(self):
        # The button's arrows spin until fresh rows (or an error) arrive,
        # so a refresh is never a click that seems to do nothing. The
        # timer is only a safety net if nothing ever answers.
        self._refresh_pending = True
        self._set_table_state("loading", True)
        set_spinning(self.refresh_btn, True)
        safety = QTimer(self.refresh_btn)  # parented, so it dies with the button
        safety.setSingleShot(True)
        safety.timeout.connect(lambda: set_spinning(self.refresh_btn, False))
        safety.start(12000)
        self.refresh_requested.emit(self.current_limit())

    def current_limit(self):
        if self.limit_combo is None:
            return None
        label = self.limit_combo.currentText()
        for option_label, value in LIMIT_OPTIONS:
            if option_label == label:
                return value
        return None

    def set_rows(self, rows: list[dict], total: int | None = None, row_color=None, empty_cells=None):
        """row_color: optional fn(row) -> QColor | None - tints that whole
        row (e.g. orders needing urgent reassignment) without changing how
        anything else about the table works.
        empty_cells: optional {column: CellNote} - what to show in that
        column where the row has no value (the row itself stays empty)."""
        self._finish_refresh(True)
        self._source_rows = rows or []
        self._render_args = (total, row_color, empty_cells)
        self._loaded_at = datetime.now()
        self._render()

    def _search_text(self) -> str:
        return self.search_box.text().strip().lower() if self.search_box is not None else ""

    def _matches(self, row: dict, query: str, core_digits: str) -> bool:
        values = [str(value) for key, value in row.items() if value is not None and key not in self.hidden_columns]
        if any(query in value.lower() for value in values):
            return True
        # 0712..., 712... and 254712... all find the same phone number.
        if len(core_digits) >= 3:
            return any(core_digits in "".join(ch for ch in value if ch.isdigit()) for value in values)
        return False

    def _on_search_changed(self, text: str):
        self._render()
        # A search should cover everything, not only the newest 50 on show:
        # in a capped list, the first letter typed loads the rest (the
        # section then hands over every row and this filter applies to them).
        limit = self.current_limit()
        if text.strip() and self.limit_combo is not None and limit is not None and len(self._source_rows) >= limit:
            self.limit_combo.setCurrentText("All")

    def _render(self):
        total, row_color, empty_cells = self._render_args
        query = self._search_text()
        if query:
            digits = "".join(ch for ch in query if ch.isdigit())
            core = digits[1:] if digits.startswith("0") else digits[3:] if digits.startswith("254") else digits
            self._rows = [row for row in self._source_rows if self._matches(row, query, core)]
        else:
            self._rows = self._source_rows
        self._generation += 1
        generation = self._generation

        if not self._rows:
            self.table.setRowCount(0)
            self.table.setColumnCount(0)
            self._content_widths = []
            self._fitted = None
            self._fit_columns()
            if query and self._source_rows:
                self.count_label.setText(self._stamp("No matches"))
            else:
                self.count_label.setText(self._stamp("No rows" if total is None else f"0 of {total}"))
            return

        columns: list[str] = []
        for row in self._rows:
            for key in row.keys():
                if key not in columns:
                    columns.append(key)

        columns = [c for c in columns if c not in self.hidden_columns]
        # "there seems to be a column that is empty" - a column with nothing
        # in any row (of all those loaded, so a search doesn't make columns
        # come and go) is left out; one with a note for empty cells stays.
        columns = [
            c for c in columns
            if (empty_cells and c in empty_cells) or any(row.get(c) not in (None, "") for row in self._source_rows)
        ]

        thumbnail_col = next((c for c, col in enumerate(columns) if col.lower() in THUMBNAIL_COLUMN_KEYS), None)
        # "text can cut and proceed in next line making the length of column
        # smaller" - long-text columns (descriptions, notes) get a capped
        # width, wrap onto a few lines and end with "...", with the whole
        # text in the cell's tooltip.
        long_cols = {
            c for c, col in enumerate(columns)
            if c != thumbnail_col and any(len(str(row.get(col) or "")) > LONG_TEXT_CHARS for row in self._source_rows)
        }

        self.table.setColumnCount(len(columns))
        self.table.setHorizontalHeaderLabels([_header_label(c) for c in columns])
        self.table.setRowCount(len(self._rows))
        status_cols = {c for c, col in enumerate(columns) if _is_status_column(col)}
        money_cols = {c for c, col in enumerate(columns) if _is_money_column(col)}
        for c in range(len(columns)):
            self.table.setItemDelegateForColumn(c, self._pill_delegate if c in status_cols else self._plain_delegate)
        right = Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        if thumbnail_col is not None:
            self.table.setIconSize(QSize(THUMBNAIL_SIZE, THUMBNAIL_SIZE))
            self.table.horizontalHeader().resizeSection(thumbnail_col, THUMBNAIL_SIZE + 16)
        for r, row in enumerate(self._rows):
            deleted = _is_deleted_row(row)
            tint = row_color(row) if row_color else None
            self.table.setRowHeight(r, THUMBNAIL_SIZE + 8 if thumbnail_col is not None else self.table.rowHeight(r))
            for c, col in enumerate(columns):
                value = row.get(col)
                if empty_cells and col in empty_cells and value in (None, ""):
                    value = empty_cells[col]
                text = "" if value is None else str(value)
                if c == thumbnail_col:
                    item = QTableWidgetItem("")
                    # The server sends pictures as "media/..." paths relative
                    # to itself, so they load wherever it runs.
                    if isinstance(value, str) and value.lower().endswith(VIDEO_SUFFIXES):
                        # A campaign video has no picture to show - a video
                        # mark, so the cell doesn't look empty.
                        item.setIcon(icon("video", theme.WOOD))
                        item.setToolTip("Video")
                    elif isinstance(value, str) and value.startswith(("http://", "https://", "media/")):
                        url = server_address.absolute(value)
                        if url in _THUMBNAILS:
                            item.setIcon(_THUMBNAILS[url])
                        else:
                            self._load_thumbnail(generation, r, c, url)
                else:
                    number = _number(value) if c in money_cols and not isinstance(value, CellNote) else None
                    if c in status_cols and not isinstance(value, CellNote):
                        text = _status_text(value)
                    elif number is not None:
                        text = _money_text(number)
                    full_text = text
                    if c in long_cols:
                        text = _preview(text)
                    item = QTableWidgetItem(text)
                    if c in long_cols and full_text:
                        item.setToolTip(full_text[:2000])
                    if number is not None:
                        item.setTextAlignment(right)
                    if isinstance(value, CellNote):
                        item.setIcon(icon(value.icon, value.color or theme.TEXT_MUTED))
                        if value.color:
                            item.setForeground(QColor(value.color))
                if tint is not None and not deleted:
                    item.setBackground(tint)
                    font = item.font()
                    font.setBold(True)
                    item.setFont(font)
                if deleted:
                    item.setBackground(DELETED_ROW_BACKGROUND)
                    item.setForeground(DELETED_ROW_FOREGROUND)
                    font = item.font()
                    font.setStrikeOut(True)
                    item.setFont(font)
                self.table.setItem(r, c, item)
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        # Measured from what each column holds - not from the header's
        # current widths, where the stretched last column would count as
        # wide and make a table that fits look too wide (the Marketing table
        # sat full width with nothing to scroll). _fit_columns then decides
        # centred-or-scrolling.
        header.setStretchLastSection(False)
        widths = []
        for c in range(len(columns)):
            if c == thumbnail_col:
                width = THUMBNAIL_SIZE + 16
            else:
                width = max(self.table.sizeHintForColumn(c), header.sectionSizeHint(c))
                if c in long_cols:
                    width = min(width, LONG_COLUMN_WIDTH)
            header.resizeSection(c, width)
            widths.append(width)
        if long_cols:
            # Wrapped cells need taller rows (up to the preview's few lines).
            self.table.resizeRowsToContents()
            least = THUMBNAIL_SIZE + 8 if thumbnail_col is not None else 38
            for r in range(self.table.rowCount()):
                if self.table.rowHeight(r) < least:
                    self.table.setRowHeight(r, least)
        self._content_widths = widths
        self._fitted = None
        self._fit_columns()

        shown = len(self._rows)
        if query:
            self.count_label.setText(self._stamp(f"Showing {shown} of {len(self._source_rows)}"))
        elif total is not None:
            self.count_label.setText(self._stamp(f"Showing {shown} of {total}"))
        else:
            self.count_label.setText(self._stamp(f"{shown} row{'s' if shown != 1 else ''}"))

    def _load_thumbnail(self, generation: int, row: int, col: int, url: str):
        def work():
            response = http.session().get(url, timeout=10)
            response.raise_for_status()
            return response.content

        def on_done(image_bytes: bytes):
            self._active_workers.remove(worker)
            # Stale if a refresh/re-sort happened while this was in
            # flight, or the row no longer exists (rows shrank).
            if generation != self._generation or row >= self.table.rowCount():
                return
            pixmap = QPixmap()
            if not pixmap.loadFromData(image_bytes):
                return
            pixmap = pixmap.scaled(
                THUMBNAIL_SIZE, THUMBNAIL_SIZE, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation
            )
            _THUMBNAILS[url] = QIcon(pixmap)
            item = self.table.item(row, col)
            if item is not None:
                item.setIcon(_THUMBNAILS[url])

        def on_error(_message: str):
            self._active_workers.remove(worker)
            # A broken/unreachable image URL just stays blank in this
            # cell - not worth surfacing as a table-wide error.

        worker = Worker(work)
        worker.signals.finished.connect(on_done)
        worker.signals.error.connect(on_error)
        self._active_workers.append(worker)
        QThreadPool.globalInstance().start(worker)

    def set_error(self, message: str):
        self._finish_refresh(False)
        self._source_rows = []
        self._rows = []
        self.table.setColumnCount(1)
        self.table.setHorizontalHeaderLabels(["Error"])
        self.table.setRowCount(1)
        self.table.setItem(0, 0, QTableWidgetItem(message))
        # An error message gets the whole card, not a measured column.
        self._content_widths = []
        self._fitted = None
        self._fit_columns()
        self.count_label.setText("")

    def set_loading(self):
        self._set_table_state("loading", True)
        self.count_label.setText("Loading...")

    def _on_cell_double_clicked(self, row: int, _col: int):
        if 0 <= row < len(self._rows):
            self.row_selected.emit(self._rows[row])

    def _on_cell_clicked(self, row: int, _col: int):
        if 0 <= row < len(self._rows):
            self.row_clicked.emit(self._rows[row])
