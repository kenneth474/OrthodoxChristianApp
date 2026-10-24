/*
  phone.ts: shows a saved phone number the way people write it.

  The server saves every number in one exact format, "+12145550123" (see
  PhoneNumbers.java). For US numbers we show "(214) 555-0123" instead;
  numbers from other countries stay as they are.
*/
export function phoneText(phone: string): string {
  const us = /^\+1(\d{3})(\d{3})(\d{4})$/.exec(phone)
  return us ? `(${us[1]}) ${us[2]}-${us[3]}` : phone
}
