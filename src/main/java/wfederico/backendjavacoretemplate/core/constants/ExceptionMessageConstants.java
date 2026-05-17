package wfederico.backendjavacoretemplate.core.constants;

/**
 * Utility class that centralizes error messages and business messages.
 * Provides constants for error messages to avoid duplicate string literals
 * and comply with PMD rules.
 */
public final class ExceptionMessageConstants {

    /* Error message when a player is not found */
    public static final  String PLAYER_NOT_FOUND = "No se encontró jugador con ese ID";
    /* Error message when a player list is empty */
    public static final  String PLAYERS_NOT_FOUND = "No hay registros de jugadores en la base de datos";
    /* Error message when a team is not found */
    public static final  String TEAM_NOT_FOUND = "No se encontró equipo con ese ID";
}

