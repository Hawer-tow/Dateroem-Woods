"""Calendar-popup date field that opens on the CURRENT month.

"the calendar is starting in 1990 make it to start from the current date
for simpler navigation" - an empty QDateEdit falls back to its minimum date,
so its popup opened decades in the past. This keeps a real date (today)
inside the editor at all times, so the popup always opens on this month,
and tracks separately whether the person has actually chosen one - so an
optional date can still be left empty and is sent as null.
"""
from PySide6.QtCore import QDate, Qt
from PySide6.QtWidgets import QDateEdit, QHBoxLayout, QLabel, QPushButton, QWidget

DATE_FORMAT = "yyyy-MM-dd"


class DatePickerField(QWidget):
    def __init__(self, value=None, parent=None):
        super().__init__(parent)
        self._is_set = False

        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        self.edit = QDateEdit()
        self.edit.setCalendarPopup(True)
        self.edit.setDisplayFormat(DATE_FORMAT)
        self.edit.setMinimumWidth(150)
        self.edit.setMinimumHeight(34)

        # Roomy popup - big enough to read and to hit a day comfortably.
        calendar = self.edit.calendarWidget()
        calendar.setMinimumSize(440, 340)
        calendar.setGridVisible(True)
        calendar.setFirstDayOfWeek(Qt.DayOfWeek.Monday)
        calendar.clicked.connect(lambda _date: self._mark_set())  # re-picking today still counts

        layout.addWidget(self.edit)

        self.hint = QLabel("not set")
        self.hint.setProperty("role", "muted")
        layout.addWidget(self.hint)

        self.clear_button = QPushButton("Clear")
        self.clear_button.setToolTip("Leave this date empty")
        self.clear_button.clicked.connect(self.clear)
        layout.addWidget(self.clear_button)
        layout.addStretch()

        self.edit.dateChanged.connect(lambda _date: self._mark_set())
        self.set_value(value)

    def _mark_set(self):
        self._is_set = True
        self._refresh_state()

    def _refresh_state(self):
        self.edit.setProperty("unset", not self._is_set)
        self.edit.style().unpolish(self.edit)
        self.edit.style().polish(self.edit)
        self.hint.setVisible(not self._is_set)
        self.clear_button.setEnabled(self._is_set and self.isEnabled())

    def clear(self):
        self.edit.blockSignals(True)
        self.edit.setDate(QDate.currentDate())
        self.edit.blockSignals(False)
        self._is_set = False
        self._refresh_state()

    def set_value(self, value):
        # Accepts "2026-09-21" or a longer ISO timestamp; anything empty or
        # unparseable leaves the field unset (showing today).
        parsed = QDate.fromString(str(value)[:10], DATE_FORMAT) if value else QDate()
        if not parsed.isValid():
            self.clear()
            return
        self.edit.blockSignals(True)
        self.edit.setDate(parsed)
        self.edit.blockSignals(False)
        self._is_set = True
        self._refresh_state()

    def value(self):
        return self.edit.date().toString(DATE_FORMAT) if self._is_set else None

    def setEnabled(self, enabled: bool):  # noqa: N802 - Qt naming
        super().setEnabled(enabled)
        self._refresh_state()
