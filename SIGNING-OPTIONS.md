# Publisher signing options

Checked on 2 October 2026 against the providers' own pages. Prices and stock can change.

## Recommendation

Start with a Microsoft Store MSIX edition if zero-cost signing and a simple install link are the priority. Keep the existing standalone EXE for owner testing. Store approval is required; there is no instant certificate we can apply automatically to the current EXE.

A Store edition needs a separate deployment mode: Windows installs, updates and removes the launcher package. The launcher continues to fetch the signed modpack from GitHub into a writable game-data folder. It must not copy or replace its own Store executable or use the preview's app-only removal worker. Existing worlds and launcher profiles need an explicit migration test. This adaptation is not implemented in 0.7.0.

Microsoft provides free signing and hosting for Store MSIX submissions. The EXE/MSI submission route still requires the publisher to sign the installer. New developer onboarding at storedeveloper.microsoft.com is free, but asks the account owner for government ID and a selfie. Account creation, identity verification and accepting publishing agreements remain owner steps.

Sources: [Microsoft signing comparison](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options), [account registration](https://learn.microsoft.com/en-us/windows/apps/publish/partner-center/open-a-developer-account), [MSIX requirements](https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/app-package-requirements?pivots=store-installer-msix).

## Keeping a downloadable EXE

| Option | Current cost indication | Practical limit |
| --- | --- | --- |
| SignPath Foundation | Free for approved open-source projects | Approval, verifiable builds, an OSI license, project reputation and MFA are required. Publisher is SignPath Foundation. |
| Certum Open Source cloud signing | From EUR 49 | Product page currently says out of stock. Requires qualifying open-source software and identity validation; publisher includes the developer's real identity. |
| Microsoft Artifact Signing | About USD 9.99 per month | EU organizations are supported; individual applicants are currently limited to USA and Canada. A Latvian individual cannot use that route. |
| Certum Standard cloud signing | From EUR 209 | Identity validation required; the checked product page also reports out of stock. Check current availability before paying. |

The repository currently exposes source but has no root open-source license. Publishing code alone does not establish eligibility for the open-source programs. The GitHub Actions build is also blocked by an account billing lock. Those are preparation tasks before a SignPath application; approval is not guaranteed.

Sources: [SignPath requirements](https://signpath.org/terms.html), [Certum open-source cloud product](https://shop.certum.eu/open-source-code-signing-on-simplysign.html), [Certum standard cloud product](https://shop.certum.eu/standard-code-signing-in-the-cloud.html), [Microsoft availability and costs](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options).

## What a signature fixes

A trusted Authenticode signature verifies the publisher and the signed file. Setting Company to pjampjam is only file metadata. A certificate normally displays a validated identity, not an arbitrary nickname. Our RSA-signed update catalog verifies launcher updates but does not become a Windows publisher certificate.

New signed standalone applications may still show SmartScreen while reputation builds. Paying extra for EV solely to eliminate that warning is not recommended by Microsoft's current documentation. Signing is not an antivirus clearance or a guarantee against future detections. Self-signed certificates are unsuitable for friends' public downloads and we will not ask them to install a root certificate.

Sign the final EXE and timestamp it before generating its SHA-256 catalog and release signature. Signing changes the executable bytes, so a signed build must get a new verified artifact and version. Never replace the same-version release after its catalog has shipped.

No purchase, account registration, license change or external signing application has been made on the owner's behalf.
