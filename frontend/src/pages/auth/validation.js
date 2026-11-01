// Mirrors the backend bean-validation rules so users see errors immediately.
// The backend re-validates everything; these checks are only for convenience.
export const USERNAME = /^[a-zA-Z0-9_.]{3,20}$/
export const PHONE = /^01[3-9][0-9]{8}$/
export const NAME = /^[\p{L} .'-]{2,60}$/u
export const PLATE = /^[A-Za-z0-9 -]{3,20}$/

export function validateAccount(form, nameKey) {
  const e = {}
  if (!NAME.test((form[nameKey] || '').trim())) e[nameKey] = 'Enter your name (letters only, 2-60 characters).'
  if (!USERNAME.test((form.username || '').trim())) e.username = '3-20 letters, digits, "_" or "."'
  if (!PHONE.test(form.phone || '')) e.phone = 'Enter an 11-digit mobile number like 01711000001.'
  if ((form.password || '').length < 6) e.password = 'Use at least 6 characters.'
  return e
}
