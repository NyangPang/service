import logo from '@/assets/its-logo.svg'
import logoLight from '@/assets/its-logo-white.svg'

/** 잇츠 로고 — 다크모드에서는 밝은 로고 (기본 로고의 검은 글자가 어두운 바탕에서 안 보임) */
export function AppLogo({ className = '' }: { className?: string }) {
  return (
    <>
      <img src={logo} alt="잇츠" className={`dark:hidden ${className}`} />
      <img src={logoLight} alt="잇츠" className={`hidden dark:block ${className}`} />
    </>
  )
}
