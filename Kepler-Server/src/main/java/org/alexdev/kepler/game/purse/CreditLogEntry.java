package org.alexdev.kepler.game.purse;

import java.time.LocalDateTime;

/**
 * One row of {@code users_transactions} as shown in the purse credit log.
 *
 * @param type          internal transaction type (catalogue_purchase, dice_bet, ...)
 * @param creditsChange signed credit movement
 * @param createdAtUtc  wall-clock UTC time of the movement, or null for legacy rows without a date
 */
public record CreditLogEntry(String type, int creditsChange, LocalDateTime createdAtUtc) {
}
