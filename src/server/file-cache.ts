/** Node 端持久化 API 缓存：重启后仍能离线命中已获取内容。 */
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import type { ApiCache, CachedResponse } from '../shared/api.ts';

export class FileApiCache implements ApiCache {
  private dir: string;
  private maxEntries: number;

  constructor(dir: string, maxEntries = 4000) {
    this.dir = dir;
    this.maxEntries = maxEntries;
  }

  private pathFor(key: string): string {
    const hash = createHash('sha1').update(key).digest('hex');
    return join(this.dir, hash.slice(0, 2), `${hash}.json`);
  }

  async get(key: string): Promise<CachedResponse | null> {
    try {
      const raw = await readFile(this.pathFor(key), 'utf8');
      const parsed = JSON.parse(raw) as { expires: number; value: CachedResponse };
      if (parsed.expires < Date.now()) {
        void rm(this.pathFor(key), { force: true });
        return null;
      }
      return parsed.value;
    } catch {
      return null;
    }
  }

  async put(key: string, value: CachedResponse, ttlSeconds: number): Promise<void> {
    const path = this.pathFor(key);
    try {
      await mkdir(join(path, '..'), { recursive: true });
      await writeFile(path, JSON.stringify({ expires: Date.now() + ttlSeconds * 1000, value }));
    } catch {
      /* 磁盘满时忽略 */
    }
  }

  get limit(): number {
    return this.maxEntries;
  }
}
