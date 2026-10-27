export interface FooterLink {
  label: string;
  href: string;
}

export interface FooterLinkGroup {
  title: string;
  links: FooterLink[];
}

export const PRODUCT_LINKS: FooterLinkGroup = {
  title: "Product",
  links: [
    { label: "Features", href: "#features" },
    { label: "Pricing", href: "#" },
    { label: "Testimonials", href: "#testimonials" },
    { label: "Case Studies", href: "#" },
  ],
};

export const COMPANY_LINKS: FooterLinkGroup = {
  title: "Company",
  links: [
    { label: "About", href: "#" },
    { label: "Careers", href: "#" },
    { label: "Blog", href: "#" },
    { label: "Contact", href: "#" },
  ],
};

export const LEGAL_LINKS: FooterLinkGroup = {
  title: "Legal",
  links: [
    { label: "Privacy Policy", href: "#" },
    { label: "Terms of Service", href: "#" },
    { label: "Cookies", href: "#" },
  ],
};
