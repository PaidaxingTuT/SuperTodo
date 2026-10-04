'use strict';
function completeSubtasks(item) {
  if(item.done && Array.isArray(item.subtasks)) item.subtasks.forEach(task => { if(task && typeof task==='object') task.done = true; });
}
// Each occurrence creates at most one successor, including after undo/re-complete.
function createRepeatItem(items, item, now = new Date()) {
  const days = Number(item.repeatDays);
  if(!item.done || item.repeatNextId || !Number.isInteger(days) || days < 1 || days > 36500) return;
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  let date = /^\d{4}-\d{2}-\d{2}$/.test(item.due || '') ? Date.parse(item.due + 'T00:00:00Z') : today;
  if(!Number.isFinite(date)) date = today;
  const step = days * 86400000;
  date += Math.max(1, Math.floor((today - date) / step) + 1) * step;
  const next = JSON.parse(JSON.stringify(item));
  next.id = Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  next.due = new Date(date).toISOString().slice(0, 10);
  next.done = false;
  next.doneScenes = [];
  next.doneTypes = [];
  if(Array.isArray(next.subtasks)) next.subtasks.forEach(task => { if(task && typeof task==='object') task.done = false; });
  next.created = now.getTime();
  delete next.repeatNextId;
  item.repeatNextId = next.id;
  items.push(next);
  return next;
}
