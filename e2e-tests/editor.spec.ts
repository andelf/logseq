import { expect } from '@playwright/test'
import { test } from './fixtures'
import { createRandomPage, enterNextBlock, IsMac } from './utils'

test('hashtag and quare brackets in same line #4178', async ({ page }) => {
  await createRandomPage(page)

  await page.type('textarea >> nth=0', '#foo bar')
  await enterNextBlock(page)
  await page.type('textarea >> nth=0', 'bar [[blah]]', { delay: 100})

  for (let i = 0; i < 12; i++) {
    await page.press('textarea >> nth=0', 'ArrowLeft')
  }
  await page.type('textarea >> nth=0', ' ')
  await page.press('textarea >> nth=0', 'ArrowLeft')

  await page.type('textarea >> nth=0', '#')
  await page.waitForSelector('text="Search for a page"', { state: 'visible' })

  await page.type('textarea >> nth=0', 'fo')

  await page.click('.absolute >> text=' + 'foo')

  expect(await page.inputValue('textarea >> nth=0')).toBe(
    '#foo bar [[blah]]'
  )
})

// FIXME: ClipboardItem is not defined when running with this test
// test('copy & paste block ref and replace its content', async ({ page }) => {
//   await createRandomPage(page)

//   await page.type('textarea >> nth=0', 'Some random text')
//   if (IsMac) {
//     await page.keyboard.press('Meta+c')
//   } else {
//     await page.keyboard.press('Control+c')
//   }

//   await page.pause()

//   await page.press('textarea >> nth=0', 'Enter')
//   if (IsMac) {
//     await page.keyboard.press('Meta+v')
//   } else {
//     await page.keyboard.press('Control+v')
//   }
//   await page.keyboard.press('Escape')

//   const blockRef$ = page.locator('.block-ref >> text="Some random text"');

//   // Check if the newly created block-ref has the same referenced content
//   await expect(blockRef$).toHaveCount(1);

//   // Edit the last block
//   await blockRef$.press('Enter')

//   // Move cursor into the block ref
//   for (let i = 0; i < 4; i++) {
//     await page.press('textarea >> nth=0', 'ArrowLeft')
//   }

//   // Trigger replace-block-reference-with-content-at-point
//   if (IsMac) {
//     await page.keyboard.press('Meta+Shift+r')
//   } else {
//     await page.keyboard.press('Control+Shift+v')
//   }
// })
