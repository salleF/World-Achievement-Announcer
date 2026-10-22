package br.sinosdomundo.api;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Disparado antes de uma conquista ser anunciada. Outros plugins podem cancelar o anúncio ou mudar a
 * lista de contribuidores.
 */
public final class ConquestAnnounceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String conquestId;
    private final String display;
    private final List<String> contributors;
    private boolean cancelled;

    public ConquestAnnounceEvent(String conquestId, String display, List<String> contributors) {
        this.conquestId = conquestId;
        this.display = display;
        this.contributors = new ArrayList<>(contributors);
    }

    /** Id da conquista no config, ou vazio para anúncios avulsos. */
    public String getConquestId() {
        return conquestId;
    }

    /** Nome mostrado (MiniMessage). */
    public String getDisplay() {
        return display;
    }

    /** Lista editável de contribuidores, do maior dano para o menor. */
    public List<String> getContributors() {
        return contributors;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
