/* Render both capture documents using GFM, then check images and inspectable previews. */
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {createHash} = require('node:crypto');

async function main() {
  const {marked} = await import(pathToFileURL(require.resolve('marked')).href);
  const {chromium} = require('playwright');
  const root = path.resolve(__dirname, '../..');
  const repo = path.resolve(root, '..');
  const output = path.join(repo, 'build/capture-docs/preview');
  fs.mkdirSync(output, {recursive:true});
  const documents = [path.join(root, 'capture/capture.md'), path.join(root, 'capture/README.md')];
  const previews = [];
  for (const [index, file] of documents.entries()) {
    const md = fs.readFileSync(file, 'utf8');
    const seen = new Map();
    const renderer = new marked.Renderer();
    renderer.heading = function ({tokens, depth}) {
      const html = this.parser.parseInline(tokens);
      const slug = html.replace(/<[^>]*>/g,'').toLowerCase().replace(/[^\p{L}\p{N}_ -]/gu,'').replace(/ /g,'-');
      const occurrence = seen.get(slug) || 0;
      seen.set(slug, occurrence + 1);
      return `<h${depth} id="${slug}${occurrence ? '-'+occurrence : ''}">${html}</h${depth}>\n`;
    };
    const html = marked.parse(md, {gfm:true, renderer});
    const name = index ? 'detailed' : 'shared';
    const destination = path.join(output, name+'.html');
    const css = `body{margin:0;padding:32px;background:#f6f8fa;color:#1f2328;font:16px/1.65 'Malgun Gothic',sans-serif}article{max-width:1100px;margin:auto;background:white;padding:40px;border:1px solid #d1d9e0;border-radius:8px}h1,h2{border-bottom:1px solid #d1d9e0;padding-bottom:.3em}h2{margin-top:2em}h3{margin-top:1.6em}a{color:#0969da}table{border-collapse:collapse;width:100%;margin:16px 0}th,td{border:1px solid #d1d9e0;padding:9px 12px;text-align:left;overflow-wrap:anywhere}th{background:#f6f8fa}tr:nth-child(even){background:#fafbfc}code{font:90%/1.6 Consolas,'Malgun Gothic',monospace;background:#eff1f3;overflow-wrap:anywhere}pre{background:#f6f8fa;padding:16px;overflow-x:auto}img{display:block;max-width:100%;height:auto;margin:20px auto}blockquote{color:#59636e;border-left:4px solid #d1d9e0;margin-left:0;padding-left:16px}@media print{body{padding:0;background:white}article{max-width:none;border:0;padding:0}h2,h3{break-after:avoid}img,table{break-inside:avoid}}`;
    fs.writeFileSync(destination, `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>${name} capture documentation</title><base href="${pathToFileURL(path.dirname(file)+path.sep).href}"><style>${css}</style></head><body><article>${html}</article></body></html>`);
    previews.push({name,source:path.relative(repo,file).replaceAll('\\','/'),destination,
      sourceSha256:createHash('sha256').update(md.replaceAll('\r\n','\n')).digest('hex')});
  }
  const browserPath = process.env.CAPTURE_BROWSER || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  ].find(p=>fs.existsSync(p));
  const browser = await chromium.launch({headless:true,...(browserPath?{executablePath:browserPath}:{})});
  const results=[];
  try {
    for (const preview of previews) {
      const page=await browser.newPage({viewport:{width:1280,height:960},deviceScaleFactor:1});
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      page.on('requestfailed',r=>errors.push(r.url()+': '+r.failure()?.errorText));
      await page.goto(pathToFileURL(preview.destination).href,{waitUntil:'load'});
      await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));});
      const state=await page.evaluate(()=>({
        images:[...document.images].map(i=>({src:i.getAttribute('src'),loaded:i.complete&&i.naturalWidth>0,natural:[i.naturalWidth,i.naturalHeight],display:[i.clientWidth,i.clientHeight],visible:getComputedStyle(i).display!=='none'})),
        missingAnchors:[...document.querySelectorAll('a[href^="#"]')].map(a=>a.hash.slice(1)).filter(id=>!document.getElementById(decodeURIComponent(id))),
        horizontalOverflow:document.documentElement.scrollWidth>innerWidth,
        headings:[...document.querySelectorAll('h2')].map(h=>h.textContent)
      }));
      const expectedFigures = preview.name === 'shared' ? 9 : 6;
      if(state.images.length!==expectedFigures||state.images.some(i=>!i.loaded||!i.visible||i.display[0]<500)||state.missingAnchors.length||state.horizontalOverflow||errors.length)
        throw new Error(JSON.stringify({preview:preview.name,...state,errors}));
      await page.screenshot({path:path.join(output,preview.name+'-overview.png')});
      for(let i=0;i<state.images.length;i++)
        await page.locator('img').nth(i).screenshot({path:path.join(output,`${preview.name}-figure-${i+1}.png`)});
      results.push({document:preview.source,sourceSha256:preview.sourceSha256,...state,errors});
      await page.close();
    }
  } finally {await browser.close();}
  fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify({renderer:'marked (GFM)',
    versions:{marked:require('marked/package.json').version,playwright:require('playwright/package.json').version},
    checkedAt:new Date().toISOString(),results},null,2)+'\n');
  console.log(`PASS: ${results.length} Markdown previews, 15 loaded inline diagrams, internal anchors, no horizontal overflow`);
  console.log(output);
}
main().catch(error=>{console.error(error);process.exitCode=1;});
