package hu.rmz.prefiscore.model

/** A repóban levő fotó (signal-2026-11-26-20-52-11-201.jpg) kézzel átírt tartalma. */
object SheetFixtures {
    val photo = SheetScan(
        players = listOf(
            ScannedPlayer(
                "Szabó", listOf(0, 5, 13, 4, 3, 13),
                listOf(ScannedRoom("Boti", listOf(45, 63, 98)), ScannedRoom("Laci", listOf(4)), ScannedRoom("Zsomi", emptyList())),
            ),
            ScannedPlayer(
                "Boti", listOf(0, 9, 15, 22, 14, 9, 5, 6, 2, 6, 10),
                listOf(
                    ScannedRoom("Szabó", listOf(10, 55, 90, 140)),
                    ScannedRoom("Laci", listOf(16, 46, 106, 176, 246)),
                    ScannedRoom("Zsomi", listOf(45, 90)),
                ),
            ),
            ScannedPlayer(
                "Laci", listOf(0, 4, 10, 5, 3, 21, 35, 49),
                listOf(
                    ScannedRoom("Szabó", listOf(10, 42, 87, 132, 182)),
                    ScannedRoom("Boti", listOf(45, 57, 92, 100, 110, 114, 134)),
                    ScannedRoom("Zsomi", listOf(45, 90)),
                ),
            ),
            ScannedPlayer(
                "Zsomi", listOf(0, 9, 10, 19, 13, 15),
                listOf(
                    ScannedRoom("Szabó", emptyList()),
                    ScannedRoom("Boti", listOf(8, 18, 30, 42, 46)),
                    ScannedRoom("Laci", listOf(30, 90, 160, 230)),
                ),
            ),
        ),
        druzsTotal = 12,
        druzsCrossed = 3,
        confidence = "medium",
        notes = "Boti 5. fenti értéke (14) javított számjegy.",
    )
}
