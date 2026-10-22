// Testa os gatilhos de conquista com bots no servidor local.
// Precisa no config de teste: primeira-vaca (MATAR_MOB COW), boss-de-evento (MOB_MARCADO),
// primeira-pedra (CONQUISTA minecraft:story/mine_stone) e primeiros-no-nether (DIMENSAO world_nether).
// Uso: node teste_conquistas.js
const mineflayer = require('mineflayer');

const HOST = '127.0.0.1';
const PORT = 25565;
const VERSION = '1.21.4';
const Y = -60;

const espera = (ms) => new Promise((r) => setTimeout(r, ms));
const t0 = Date.now();
const agora = () => ((Date.now() - t0) / 1000).toFixed(1) + 's';
const avisos = [];

function entrar(nome) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username: nome, version: VERSION, auth: 'offline' });
    bot.on('resourcePack', () => bot.acceptResourcePack());
    if (nome === 'Organizador') {
      bot.on('messagestr', (m) => {
        if (m.includes('[Sino]') || m.startsWith('- ') || m.includes('Mob marcado') || m.includes('Olhe para')) {
          avisos.push(`${agora()} ${m}`);
        }
      });
    }
    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', (motivo) => reject(new Error(`${nome} foi expulso: ${JSON.stringify(motivo)}`)));
    bot.once('error', reject);
  });
}

function maisPerto(bot, tipo) {
  return bot.nearestEntity((e) => e.name === tipo);
}

/** Os bots batem no mob até ele morrer. */
async function matar(bots, tipo) {
  const alvo = maisPerto(bots[0], tipo);
  if (!alvo) throw new Error('Não achei ' + tipo);
  const id = alvo.id;
  for (let i = 0; i < 80; i++) {
    let vivo = false;
    for (const bot of bots) {
      const e = bot.entities[id];
      if (e) {
        vivo = true;
        await bot.lookAt(e.position.offset(0, 0.6, 0), true);
        bot.attack(e);
      }
    }
    if (!vivo) return;
    await espera(600);
  }
  throw new Error(tipo + ' não morreu');
}

async function main() {
  const org = await entrar('Organizador');
  await espera(4500);
  const b = await entrar('Bot_B');
  await espera(4500);
  const c = await entrar('Bot_C');
  await espera(3000);

  const cmd = async (texto, ms = 600) => { org.chat(texto); await espera(ms); };
  await cmd('/sinos resetar primeira-vaca');
  await cmd('/sinos resetar primeira-pedra');
  await cmd('/sinos resetar primeiros-no-nether');
  await cmd('/kill @e[type=!player]');
  await cmd(`/tp Organizador 0 ${Y} -4 0 0`);
  await cmd(`/tp Bot_B 2 ${Y} 0 90 0`);
  await cmd(`/tp Bot_C -2 ${Y} 0 -90 0`);

  console.log('1) Vaca: Bot_B e Bot_C batem');
  await cmd(`/summon cow 0 ${Y} 0 {NoAI:1b}`, 1500);
  await matar([b, c], 'cow');
  await espera(2000);

  console.log('2) Segunda vaca: não deve tocar (uma-vez)');
  await cmd(`/summon cow 0 ${Y} 0 {NoAI:1b}`, 1500);
  await matar([b, c], 'cow');
  await espera(2000);

  console.log('3) Porco marcado: só Bot_C bate');
  await cmd(`/summon pig 0 ${Y} 0 {NoAI:1b}`, 1500);
  const porco = maisPerto(org, 'pig');
  await org.lookAt(porco.position.offset(0, 0.5, 0), true);
  await espera(500);
  await cmd('/sinos marcar boss-de-evento', 1000);
  await matar([c], 'pig');
  await espera(2000);

  console.log('4) Advancement para Bot_B');
  await cmd('/advancement grant Bot_B only minecraft:story/mine_stone', 1500);

  console.log('5) Nether: Bot_C e depois Bot_B');
  await cmd('/execute in minecraft:the_nether run tp Bot_C 0 130 0', 1500);
  await cmd('/execute in minecraft:the_nether run tp Bot_B 2 130 0', 8000);

  // Os anúncios entram em fila (cada um dura uns 10 s); espera todos saírem no chat
  await espera(45000);
  await cmd('/sinos lista', 2000);

  console.log('\nMensagens recebidas pelo Organizador:');
  for (const a of avisos) console.log('  ' + a);

  for (const bot of [org, b, c]) bot.quit();
  await espera(1000);
  process.exit(0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
