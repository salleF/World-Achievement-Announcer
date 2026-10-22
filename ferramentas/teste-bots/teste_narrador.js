// Testa música de fundo, frase final e troca de narrador.
// Precisa no config de teste da conquista manual teste-dos-bots. Uso: node teste_narrador.js
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));
let t0 = Date.now();
const agora = () => ((Date.now() - t0) / 1000).toFixed(1) + 's';

function entrar(nome) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: nome, version: '1.21.4', auth: 'offline' });
    bot.sons = [];
    bot.msgs = [];
    bot.on('resourcePack', () => bot.acceptResourcePack());
    bot.on('soundEffectHeard', (n) => bot.sons.push(`${n}@${agora()}`));
    bot.on('messagestr', (m) => bot.msgs.push(m));
    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', (m) => reject(new Error(nome + ' expulso: ' + JSON.stringify(m))));
    bot.once('error', reject);
  });
}

function relatorio(titulo, bot) {
  const voz = bot.sons.filter((s) => s.includes('sinos:voz'));
  const musica = bot.sons.filter((s) => s.includes('sinos:musica'));
  console.log(`\n${titulo}`);
  console.log(`  falas: ${voz.length}  (${voz.map((s) => s.split('.')[1]).join(' ')})`);
  console.log(`  música: ${musica.join(', ') || '(nenhuma)'}`);
  console.log(`  chat: ${bot.msgs.filter((m) => m.includes('[Sino]') || m.includes('Narrador')).join(' | ')}`);
}

async function main() {
  const org = await entrar('Organizador');
  await espera(4500);
  const b = await entrar('Bot_B');
  await espera(3000);

  org.sons = []; org.msgs = []; t0 = Date.now();
  org.chat('/sinos anunciar teste-dos-bots Organizador,Bot_B');
  await espera(15000);
  relatorio('1) Narrador cadu (ativo)', org);
  const vozCadu = org.sons.filter((s) => s.includes('sinos:voz')).map((s) => s.split('@')[0]);

  org.sons = []; org.msgs = [];
  org.chat('/sinos narrador clara');
  for (let i = 0; i < 120 && !org.msgs.some((m) => m.includes('Narrador trocado')); i++) await espera(1000);
  await espera(3000);
  org.sons = []; t0 = Date.now();
  org.chat('/sinos anunciar teste-dos-bots Organizador,Bot_B');
  await espera(15000);
  relatorio('2) Depois de /sinos narrador clara', org);
  const vozClara = org.sons.filter((s) => s.includes('sinos:voz')).map((s) => s.split('@')[0]);
  console.log('\nFalas diferentes entre os narradores: ' + (vozCadu.length > 0 && vozClara.length > 0 && !vozClara.some((s) => vozCadu.includes(s))));

  org.chat('/sinos narrador cadu');
  await espera(4000);
  for (const bot of [org, b]) bot.quit();
  await espera(1000);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
