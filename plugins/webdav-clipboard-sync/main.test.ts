// webdav-clipboard-sync 测试：Basic Auth 凭据换行清理 + push body 文本保持字符串。
// 运行：仓库根 `xipm test plugins/webdav-clipboard-sync`。

const DAV_URL = 'https://dav.example.com/dav/';
const REMOTE_FILE = 'https://dav.example.com/dav/clipboard/current.json';

function lastRequest(): XimeTestMockRequest {
  return __ximeMock.httpRequests[__ximeMock.httpRequests.length - 1];
}

test('用户名/密码含换行会被清理，Authorization 为单行 Basic 头', async () => {
  __ximeMock.setConfig('davUrl', DAV_URL);
  __ximeMock.setConfig('remotePath', '');
  __ximeMock.setConfig('username', 'alice\n');
  __ximeMock.setConfig('password', 'secret\r\n');
  __ximeMock.addHttpResponse('PUT', REMOTE_FILE, { status: 201 });

  const p = (globalThis as any).plugin;
  const ok = await p.clipboardSync.push({
    type: 'text', hash: 'h1', text: 'hi', hasData: false, dataName: null, size: 2, source: null,
  });
  assert.ok(ok === true, 'push 应成功');

  const auth = lastRequest().headers['Authorization'];
  const expected = 'Basic ' + host.crypto.base64(new TextEncoder().encode('alice:secret'));
  assert.equal(auth, expected, '凭据中的换行应被清除');
  assert.equal(auth.indexOf('\n'), -1, 'Authorization 请求头不应含换行');
});

test('push body 的 text 字段序列化为字符串（纯数字不被当数字）', async () => {
  __ximeMock.setConfig('davUrl', DAV_URL);
  __ximeMock.setConfig('remotePath', '');
  __ximeMock.setConfig('username', '');
  __ximeMock.setConfig('password', '');
  __ximeMock.addHttpResponse('PUT', REMOTE_FILE, { status: 201 });

  const p = (globalThis as any).plugin;
  await p.clipboardSync.push({
    type: 'text', hash: 'h2', text: '13100076519', hasData: false, dataName: null, size: 11, source: null,
  });

  const body = JSON.parse(lastRequest().text ?? '');
  assert.equal(typeof body.text, 'string', 'text 必须序列化为字符串');
  assert.equal(body.text, '13100076519');
});
