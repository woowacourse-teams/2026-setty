import { useId, type SVGProps } from 'react';

export type MarketplaceIconName =
  'close' | 'inbox' | 'message' | 'mine' | 'refresh' | 'right' | 'undo';

interface MarketplaceIconProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  name: MarketplaceIconName;
  title?: string;
}

function IconPath({ name }: Pick<MarketplaceIconProps, 'name'>) {
  switch (name) {
    case 'close':
      return (
        <>
          <path d="M6 6l12 12" />
          <path d="M18 6 6 18" />
        </>
      );
    case 'inbox':
    case 'message':
      return (
        <>
          <rect x="3.25" y="5.25" width="17.5" height="13.5" rx="2.5" />
          <path d="m4.5 7 7.5 5.7L19.5 7" />
        </>
      );
    case 'mine':
      return (
        <>
          <circle cx="12" cy="8.25" r="3.25" />
          <path d="M5.5 19c.8-3.4 3-5.1 6.5-5.1s5.7 1.7 6.5 5.1" />
        </>
      );
    case 'refresh':
      return (
        <>
          <path d="M19 7v5h-5" />
          <path d="M18.1 12a6.5 6.5 0 1 0-1.9 4.6" />
        </>
      );
    case 'right':
      return (
        <>
          <path d="M5 12h14" />
          <path d="m14 7 5 5-5 5" />
        </>
      );
    case 'undo':
      return (
        <>
          <path d="m9 7-4 4 4 4" />
          <path d="M5 11h8a6 6 0 1 1 0 12" transform="translate(0 -4)" />
        </>
      );
  }
}

export default function MarketplaceIcon({ name, title, ...props }: MarketplaceIconProps) {
  const titleId = useId();

  return (
    <svg
      aria-hidden={title ? undefined : true}
      aria-labelledby={title ? titleId : undefined}
      fill="none"
      focusable="false"
      role={title ? 'img' : undefined}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
      {...props}
    >
      {title ? <title id={titleId}>{title}</title> : null}
      <IconPath name={name} />
    </svg>
  );
}
