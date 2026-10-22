// Testa o envio do pacote (sem remover pacotes de outros plugins) e o anúncio com ou sem voz.
// Uso: node teste_grupo1.js
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Organizador', version: '1.21.4', auth: 'offline' });
  const eventos = [];
  let sinos = 0;
  let falas = 0;
  bot._client.on('remove_resource_pack', (d) => eventos.push('REMOVER ' + (d.uuid ? d.uuid : 'TODOS')));
  bot._client.on('add_resource_pack', (d) => eventos.push(`ADICIONAR ${d.url} hash="${d.hash}"`));
  bot.on('resourcePack', () => bot.acceptResourcePack());
  bot.on('messagestr', (m) => { if (/Sino|Erro|erro|não/.test(m)) eventos.push('chat: ' + m.slice(0, 80)); });
  bot.on('entitySpawn', (e) => { if (e.name === 'item_display') sinos++; });
  bot.on('soundEffectHeard', (n) => { if (String(n).includes('sinos:voz')) falas++; });
  await new Promise((r) => bot.once('spawn', r));
  await espera(5000);
  bot.chat('/sinos anunciar teste-dos-bots Organizador');
  await espera(12000);
  console.log('Pacotes:', eventos.join(' | ') || '(nenhum)');
  console.log(`Sino: ${sinos} peça(s) vista(s); falas da voz: ${falas}`);
  bot.chat('/stop');
  await espera(2000);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
