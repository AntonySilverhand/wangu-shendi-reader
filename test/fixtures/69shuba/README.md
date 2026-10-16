# 69书吧 fixtures

这些页面是**按 69书吧（jieqi 模板）已知结构手工重建的合成样例**，不是线上抓取：
开发环境访问 www.69shuba.com 时整站都返回 Cloudflare 人机验证页（见
`../cloudflare-challenge.html`，那一份是真实抓取），无法取得真实页面。

- 文件以 **GBK** 编码保存（与源站一致），用于覆盖字符集解码路径。
- 书名、作者、正文都是虚构的；书号 51254 与章节号 3000100x 仅用于拼 URL。
- 结构要点：详情页 `div.booknav2` + og meta；目录页 `div#catalog li[data-num]`（倒序，
  另有 `#catalog` 之外的“最新章节”块）；章节页 `div.txtnav`（h1 / div.txtinfo /
  div#txtright / div.contentadv）+ `var bookinfo = { preview_page, next_page }`；
  搜索页 `div.newbox li`。

能从浏览器保存到真实页面时，请用真实页面替换这些样例（保持文件名），再跑
`npm test` 校验解析器。
