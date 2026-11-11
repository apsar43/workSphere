import type { ButtonHTMLAttributes, PropsWithChildren } from 'react';

type ButtonProps = PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement>>;

export function Button({ children, type = 'button', ...props }: ButtonProps) {
  return (
    <button className="button" type={type} {...props}>
      {children}
    </button>
  );
}
