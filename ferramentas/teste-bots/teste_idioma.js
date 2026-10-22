// Testa o idioma: lê os nomes dos itens do menu principal, a lista de conquistas e um anúncio com voz.
// Uso: node teste_idioma.js [conquista]
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

/** Junta todos os "text" de um nome de item (vem como NBT ou JSON). */
function textoDe(valor) {
  const partes = [];
  const andar = (v) => {
    if (v == null) return;
    if (typeof v === 'string') {
      try { andar(JSON.parse(v)); } catch { partes.push(v); }
      return;
    }
    if (Array.isArray(v)) { v.forEach(andar); return; }
    if (typeof v === 'object') {
      if (v.type && 'value' in v) { andar(v.value); return; }
      if ('text' in v) andar(v.text);
      if ('extra' in v) andar(v.extra);
      if ('value' in v) andar(v.value);
    }
  };
  andar(valor);
  return partes.join('');
}

async function main() {
  const conquista = process.argv[2] || 'first-dragon';
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Organizador', version: '1.21.4', auth: 'offline' });
  const chat = [];
  let falas = 0;
  bot.on('resourcePack', () => bot.acceptResourcePack());
  bot.on('messagestr', (m) => chat.push(m));
  bot.on('soundEffectHeard', (n) => { if (String(n).includes('sinos:voz')) falas++; });
  await new Promise((r) => bot.once('spawn', r));
  await espera(4000);

  const p = new Promise((r) => bot.once('windowOpen', (w) => setTimeout(() => r(w), 300)));
  bot.chat('/sinos');
  const w = await p;
  const nomes = [];
  for (const i of [4, 10, 11, 12, 13, 14, 15, 16, 22]) {
    const it = w.slots[i];
    if (it) nomes.push(textoDe(it.customName || (it.components || []).find((c) => c.type === 'custom_name')?.data));
  }
  console.log('Menu principal:', nomes.join(' | '));
  bot.closeWindow(w);

  chat.length = 0;
  bot.chat('/sinos lista');
  await espera(1500);
  console.log('Lista:\n  ' + chat.slice(0, 4).join('\n  '));

  chat.length = 0;
  falas = 0;
  bot.chat(`/sinos anunciar ${conquista} Organizador`);
  await espera(15000);
  console.log('Anúncio:', chat.join(' | '), `| falas da voz: ${falas}`);
  bot.quit();
  await espera(500);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
