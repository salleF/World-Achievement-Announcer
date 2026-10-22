// Testa o /sinos teste com bots no servidor local (online-mode=false).
// Uso: node teste.js grupo   -> 3 bots juntos + 1 longe: espera 2 sinos
//      node teste.js fila    -> 4 bots em fila, 20 blocos entre cada: espera 2 sinos
const mineflayer = require('mineflayer');

const HOST = '127.0.0.1';
const PORT = 25565;
const VERSION = '1.21.4';
const Y = -60; // chão do mundo plano
const DISPLAYS = ['item_display', 'text_display'];

const CENARIOS = {
  grupo: {
    Organizador: [0, 0],
    Bot_B: [3, 2],
    Bot_C: [-2, 4],
    Bot_D: [200, 0],
  },
  fila: {
    Organizador: [0, 0],
    Bot_B: [20, 0],
    Bot_C: [40, 0],
    Bot_D: [60, 0],
  },
};

let t0 = Date.now();
const agora = () => ((Date.now() - t0) / 1000).toFixed(1) + 's';
const espera = (ms) => new Promise((r) => setTimeout(r, ms));

function entrar(nome) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username: nome, version: VERSION, auth: 'offline' });
    const registro = { displays: new Map(), sons: [], chat: [] };
    bot.registro = registro;

    bot.on('entitySpawn', (e) => {
      if (DISPLAYS.includes(e.name)) {
        registro.displays.set(e.id, { tipo: e.name, inicio: e.position.clone(), fim: e.position.clone(), sumiu: false });
      }
    });
    bot.on('entityMoved', (e) => {
      const d = registro.displays.get(e.id);
      if (d) d.fim = e.position.clone();
    });
    bot.on('entityGone', (e) => {
      const d = registro.displays.get(e.id);
      if (d) d.sumiu = agora();
    });
    bot.on('soundEffectHeard', (nome, pos, volume) => registro.sons.push({ nome, pos, volume, t: agora() }));
    bot.on('hardcodedSoundEffectHeard', (id, cat, pos, volume) => registro.sons.push({ nome: 'id:' + id, pos, volume, t: agora() }));
    bot.on('messagestr', (m) => registro.chat.push(m));
    bot.on('resourcePack', (url, hash) => {
      registro.pack = { url, hash };
      bot.acceptResourcePack();
    });

    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', (motivo) => reject(new Error(`${nome} foi expulso: ${JSON.stringify(motivo)}`)));
    bot.once('error', reject);
  });
}

async function main() {
  const nomeCenario = process.argv[2] || 'grupo';
  const cenario = CENARIOS[nomeCenario];
  if (!cenario) throw new Error('Cenário desconhecido: ' + nomeCenario);

  const bots = {};
  for (const nome of Object.keys(cenario)) {
    bots[nome] = await entrar(nome);
    console.log(`${nome} entrou`);
    await espera(4500); // o Bukkit recusa conexões do mesmo IP em menos de 4 s
  }

  const org = bots.Organizador;
  for (const [nome, [x, z]] of Object.entries(cenario)) {
    org.chat(`/tp ${nome} ${x} ${Y} ${z} 0 0`);
    await espera(400);
  }
  await espera(4000);

  for (const bot of Object.values(bots)) {
    bot.registro.displays.clear();
    bot.registro.sons.length = 0;
    bot.registro.chat.length = 0;
  }

  console.log(`\n/sinos teste no cenário "${nomeCenario}"`);
  t0 = Date.now();
  org.chat(`/sinos teste Teste dos Bots por=${Object.keys(cenario).join(',')}`);
  await espera(14000);

  const sinosPorBot = {};
  for (const [nome, bot] of Object.entries(bots)) {
    const p = bot.entity.position;
    const r = bot.registro;
    const sinos = [...r.displays].filter(([, d]) => d.tipo === 'item_display');
    const textos = [...r.displays].filter(([, d]) => d.tipo === 'text_display');
    // Sino e badalo são duas entidades; o sino é a primeira criada (id menor)
    sinosPorBot[nome] = sinos.length ? [Math.min(...sinos.map(([id]) => id))] : [];
    console.log(`\n${nome} em (${p.x.toFixed(1)}, ${p.y.toFixed(1)}, ${p.z.toFixed(1)})`);
    for (const [id, d] of sinos) {
      console.log(`  sino #${id} em (${d.inicio.x.toFixed(1)}, ${d.inicio.y.toFixed(1)}, ${d.inicio.z.toFixed(1)}),` +
        ` a ${d.inicio.distanceTo(p).toFixed(1)} blocos, sumiu: ${d.sumiu}`);
    }
    console.log(`  textos vistos: ${textos.length}, pecas de sino vistas: ${sinos.length}`);
    console.log(`  todos os sons: ${r.sons.map((s) => s.nome + '@' + s.t).join(', ') || '(nenhum)'}`);
    const sinosOuvidos = r.sons.filter((s) => String(s.nome).includes('bell'));
    console.log(`  sons de sino ouvidos: ${sinosOuvidos.length}` +
      (sinosOuvidos[0] ? ` (volume ${sinosOuvidos[0].volume.toFixed(2)}, ${sinosOuvidos.map((s) => s.nome + '@' + s.t).join(', ')})` : ''));
    const voz = r.sons.filter((s) => String(s.nome).includes('sinos:voz'));
    console.log(`  pacote recebido: ${r.pack ? r.pack.url : '(nenhum)'}`);
    console.log(`  falas da voz ouvidas: ${voz.length} ${voz.map((s) => s.t).join(' ')}`);
    console.log(`  chat: ${r.chat.filter((m) => m.includes('Teste dos Bots')).join(' | ') || '(nada)'}`);
  }

  const distintos = new Set(Object.values(sinosPorBot).flat());
  console.log(`\nSinos diferentes no total: ${distintos.size}`);
  console.log('Cada bot viu exatamente 1 sino: ' + Object.values(sinosPorBot).every((l) => l.length === 1));

  for (const bot of Object.values(bots)) bot.quit();
  await espera(1000);
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
