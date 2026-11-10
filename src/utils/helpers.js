export function formatDuration(ms) {
    if (!ms || ms < 0)
        return '0:00';
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
export function formatDurationLong(ms) {
    if (!ms || ms < 0)
        return '0 min';
    const totalMinutes = Math.floor(ms / 60000);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (hours > 0)
        return `${hours} hr ${minutes} min`;
    return `${minutes} min`;
}
export function formatNumber(num) {
    if (num >= 1000000)
        return `${(num / 1000000).toFixed(1)}M`;
    if (num >= 1000)
        return `${(num / 1000).toFixed(1)}K`;
    return num.toString();
}
export function formatDate(dateString) {
    return new Date(dateString).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}
export function getImageUrl(images, preferredSize = 300) {
    if (!images?.length)
        return '';
    const sorted = [...images].sort((a, b) => Math.abs(b.width - preferredSize) - Math.abs(a.width - preferredSize));
    return sorted[0]?.url || images[0]?.url || '';
}
export function debounce(fn, delay) {
    let timeoutId;
    return (...args) => { clearTimeout(timeoutId); timeoutId = setTimeout(() => fn(...args), delay); };
}
export function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}
export function generateId() { return crypto.randomUUID(); }
export function extractTrackId(url) { const m = url.match(/track\/([a-zA-Z0-9]+)/); return m ? m[1] : null; }
export function extractAlbumId(url) { const m = url.match(/album\/([a-zA-Z0-9]+)/); return m ? m[1] : null; }
export function extractArtistId(url) { const m = url.match(/artist\/([a-zA-Z0-9]+)/); return m ? m[1] : null; }
export function extractPlaylistId(url) { const m = url.match(/playlist\/([a-zA-Z0-9]+)/); return m ? m[1] : null; }
export function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
export function classNames(...classes) { return classes.filter(Boolean).join(' '); }
