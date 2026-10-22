package br.sinosdomundo.contrib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.sinosdomundo.contrib.ContributorLedger.Mode;
import br.sinosdomundo.contrib.ContributorLedger.Participant;
import br.sinosdomundo.contrib.ContributorLedger.Rules;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContributorLedgerTest {

    private static final UUID DRAGAO = UUID.randomUUID();
    private static final Participant ANA = new Participant(UUID.randomUUID(), "Ana");
    private static final Participant BETO = new Participant(UUID.randomUUID(), "Beto");
    private static final Participant CAIO = new Participant(UUID.randomUUID(), "Caio");
    private static final Participant DUDA = new Participant(UUID.randomUUID(), "Duda");
    private static final long MIN = 60_000;
    private static final Rules PADRAO = new Rules(Mode.DANO, 2, 10, 5 * MIN);

    @Test
    void ordenaPorDano() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 50, 0);
        ledger.record(DRAGAO, BETO, 120, 0);
        ledger.record(DRAGAO, ANA, 30, 1000);

        assertEquals(List.of("Beto", "Ana"), ledger.contributors(DRAGAO, PADRAO, 2000, null, List.of()));
    }

    @Test
    void danoPequenoNaoConta() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 1000, 0);
        // 5 de dano: abaixo de 10 e abaixo de 2% de 1005
        ledger.record(DRAGAO, BETO, 5, 0);

        assertEquals(List.of("Ana"), ledger.contributors(DRAGAO, PADRAO, 0, null, List.of()));
    }

    @Test
    void porcentagemBastaMesmoComDanoBaixo() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 60, 0);
        // 4 de 64 = 6%, acima de 2%
        ledger.record(DRAGAO, BETO, 4, 0);

        assertEquals(List.of("Ana", "Beto"), ledger.contributors(DRAGAO, PADRAO, 0, null, List.of()));
    }

    @Test
    void danoAntigoForaDaJanelaNaoConta() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 500, 0);
        ledger.record(DRAGAO, BETO, 50, 9 * MIN);

        assertEquals(List.of("Beto"), ledger.contributors(DRAGAO, PADRAO, 10 * MIN, null, List.of()));
    }

    @Test
    void golpeFinalSempreEntra() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 1000, 0);
        ledger.record(DRAGAO, CAIO, 1, 0);

        assertEquals(List.of("Ana", "Caio"), ledger.contributors(DRAGAO, PADRAO, 0, CAIO, List.of()));
    }

    @Test
    void presencaIncluiQuemEstavaPerto() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 100, 0);
        Rules ambos = new Rules(Mode.AMBOS, 2, 10, 5 * MIN);

        assertEquals(List.of("Ana", "Beto", "Duda"),
                ledger.contributors(DRAGAO, ambos, 0, null, List.of(DUDA, ANA, BETO)));
    }

    @Test
    void soPresencaIgnoraDano() {
        ContributorLedger ledger = new ContributorLedger();
        ledger.record(DRAGAO, ANA, 100, 0);
        Rules presenca = new Rules(Mode.PRESENCA, 2, 10, 5 * MIN);

        assertEquals(List.of("Beto"), ledger.contributors(DRAGAO, presenca, 0, null, List.of(BETO)));
    }

    @Test
    void purgeApagaMobsParados() {
        ContributorLedger ledger = new ContributorLedger();
        UUID outro = UUID.randomUUID();
        ledger.record(DRAGAO, ANA, 10, 0);
        ledger.record(outro, ANA, 10, 9 * MIN);

        ledger.purge(10 * MIN, 5 * MIN);

        assertEquals(1, ledger.trackedCount());
        assertEquals(List.of(), ledger.contributors(DRAGAO, PADRAO, 10 * MIN, null, List.of()));
    }
}
