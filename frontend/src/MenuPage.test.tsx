import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setMockLoggedIn } from './mocks/handlers'
import { getMenu } from './api'
import { MenuPage } from './MenuPage'

async function renderMenu() {
  render(<MenuPage onUnauthorized={vi.fn()} />)
  return screen.findByRole('region', { name: 'Sandwich' })
}

describe('MenuPage', () => {
  beforeEach(() => setMockLoggedIn(true))

  it('addItemCallsApiAndShowsIt', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.type(within(sandwich).getByLabelText('New item for Sandwich'), 'Chicken curry')
    await user.click(within(sandwich).getByRole('button', { name: 'Add item' }))

    expect(await within(sandwich).findByText('Chicken curry')).toBeInTheDocument()
    const menu = await getMenu()
    expect(menu.find((f) => f.name === 'Sandwich')!.items.map((i) => i.name)).toContain('Chicken curry')
    expect(within(sandwich).getByLabelText('New item for Sandwich')).toHaveValue('')
  })

  it('duplicateShowsMessage', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.type(within(sandwich).getByLabelText('New item for Sandwich'), 'tuna')
    await user.click(within(sandwich).getByRole('button', { name: 'Add item' }))

    expect(await within(sandwich).findByRole('alert')).toHaveTextContent('That name already exists.')
  })

  it('duplicateFoodTypeShowsMessage', async () => {
    const user = userEvent.setup()
    await renderMenu()

    await user.type(screen.getByLabelText('New food type'), 'SOUP')
    await user.click(screen.getByRole('button', { name: 'Add food type' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That name already exists.')
  })

  it('addFoodTypeShowsIt', async () => {
    const user = userEvent.setup()
    await renderMenu()

    await user.type(screen.getByLabelText('New food type'), 'Salad')
    await user.click(screen.getByRole('button', { name: 'Add food type' }))

    expect(await screen.findByRole('region', { name: 'Salad' })).toBeInTheDocument()
  })

  it('blankNameShowsServerError', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.type(within(sandwich).getByLabelText('New item for Sandwich'), '   ')
    await user.click(within(sandwich).getByRole('button', { name: 'Add item' }))

    expect(await within(sandwich).findByRole('alert')).toHaveTextContent('Name must not be blank')
  })

  it('renameItemShowsNewName', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.click(within(sandwich).getByRole('button', { name: 'Rename Tuna' }))
    const input = within(sandwich).getByLabelText('New name for Tuna')
    await user.clear(input)
    await user.type(input, 'Tuna mayo')
    await user.click(within(sandwich).getByRole('button', { name: 'Save' }))

    expect(await within(sandwich).findByText('Tuna mayo')).toBeInTheDocument()
  })

  it('renameFoodTypeShowsNewName', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.click(within(sandwich).getByRole('button', { name: 'Rename Sandwich' }))
    const input = within(sandwich).getByLabelText('New name for Sandwich')
    await user.clear(input)
    await user.type(input, 'Wraps')
    await user.click(within(sandwich).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('region', { name: 'Wraps' })).toBeInTheDocument()
  })

  it('removeItemRemovesIt', async () => {
    const user = userEvent.setup()
    const sandwich = await renderMenu()

    await user.click(within(sandwich).getByRole('button', { name: 'Remove Tuna' }))

    await vi.waitFor(() => expect(within(sandwich).queryByText('Tuna')).not.toBeInTheDocument())
  })

  it('removeFoodTypeAsksConfirmation', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const sandwich = await renderMenu()

    await user.click(within(sandwich).getByRole('button', { name: 'Remove Sandwich' }))
    expect(confirm).toHaveBeenLastCalledWith('Remove Sandwich and all its items?')
    expect(screen.getByRole('region', { name: 'Sandwich' })).toBeInTheDocument()

    await user.click(within(sandwich).getByRole('button', { name: 'Remove Sandwich' }))
    await vi.waitFor(() =>
      expect(screen.queryByRole('region', { name: 'Sandwich' })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('region', { name: 'Soup' })).toBeInTheDocument()
  })
})
