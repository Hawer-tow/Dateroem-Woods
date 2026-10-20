// "the go back arrow does not feel so centered" - a drawn arrow instead of
// the "←" text character, which fonts place off-centre (worst on phones);
// this one is geometrically centred in its 24x24 box, so it sits exactly
// in the middle of the round back button.
export default function BackArrowIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
      <path
        d="M19 12H5M12 5l-7 7 7 7"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
