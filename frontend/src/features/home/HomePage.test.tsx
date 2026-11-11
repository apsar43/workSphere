import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { HomePage } from '@/features/home/HomePage';

describe('HomePage', () => {
  it('confirms that the frontend foundation is ready', async () => {
    const user = userEvent.setup();

    render(<HomePage />);
    expect(screen.getByRole('heading', { name: /one workspace/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /check foundation/i }));

    expect(screen.getByRole('status')).toHaveTextContent('Frontend foundation is ready.');
  });
});
