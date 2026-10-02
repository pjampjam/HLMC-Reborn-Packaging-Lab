# Native launcher design pass

The app remains a native WPF desktop app with its bundled runtime. No browser engine, paid template or extra framework was added. The supplied logos are displayed with proportional scaling; the premium banner uses a presentation viewport around its logo, and SKlauncher retains its proportions.

The design uses a quiet charcoal surface, a slightly darker brand sidebar, restrained yellow actions, muted green detected-launcher status, and a visible scrollbar thumb. Related controls share spacing and rounded corners. Language stays on the main page, while file selection and maintenance are in Settings. Destructive actions use a themed confirmation with a safe Cancel choice. Keyboard focus remains visible. The empty draggable title bar keeps ordinary minimize, maximize/restore and close controls.

These choices follow Microsoft's [Windows design guidance](https://learn.microsoft.com/en-us/windows/apps/design/guidelines-overview), [navigation guidance](https://learn.microsoft.com/en-us/windows/apps/design/basics/navigation-basics), and [dialog guidance](https://learn.microsoft.com/en-us/windows/apps/develop/ui/controls/dialogs-and-flyouts/dialogs). The implementation uses WPF equivalents, not WinUI ContentDialog APIs.

The requested [GetLayers](https://www.getlayers.ai/), [Landdding](https://landdding.com/), [Motion](https://www.motionin.design/) and [CollectUI](https://collectui.com/) collections informed the reference review. No protected templates, code or commercial assets were copied. Pageflows could not be retrieved during this review.

Windows-owned SmartScreen prompts and file pickers are not simulated or hidden. App-owned error and confirmation dialogs share the launcher theme. File Company metadata is separate from the verified publisher described in Microsoft's [SmartScreen guidance](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).
